package app.corkboard.connections

import app.corkboard.common.ApiException
import app.corkboard.common.ProblemCode
import app.corkboard.jooq.enums.ConnectionStatus
import app.corkboard.jooq.tables.references.CONNECTIONS
import app.corkboard.jooq.tables.references.USERS
import app.corkboard.notifications.NotificationKind
import app.corkboard.notifications.NotificationService
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private const val SEARCH_LIMIT = 10
private const val MIN_QUERY = 2

@Service
class ConnectionService(
    private val dsl: DSLContext,
    private val notifications: NotificationService,
    private val clock: Clock,
) {

    fun list(userId: UUID): ConnectionsResponse {
        val rows = dsl.select(
            CONNECTIONS.ID, CONNECTIONS.STATUS, CONNECTIONS.REQUESTER_ID, CONNECTIONS.ADDRESSEE_ID,
            CONNECTIONS.CREATED_AT, CONNECTIONS.ANSWERED_AT,
            USERS.ID, USERS.HANDLE, USERS.DISPLAY_NAME, USERS.AVATAR_SEED, USERS.CREATED_AT,
        )
            .from(CONNECTIONS)
            .join(USERS).on(USERS.ID.eq(otherSide(userId)))
            .where(mine(userId), CONNECTIONS.STATUS.ne(ConnectionStatus.declined))
            .orderBy(CONNECTIONS.CREATED_AT.desc())
            .fetch()

        val connected = mutableListOf<ConnectionItem>()
        val incoming = mutableListOf<ConnectionItem>()
        val outgoing = mutableListOf<ConnectionItem>()
        for (r in rows) {
            val accepted = r[CONNECTIONS.STATUS] == ConnectionStatus.accepted
            val state = when {
                accepted -> ConnectionState.CONNECTED
                r[CONNECTIONS.ADDRESSEE_ID] == userId -> ConnectionState.INCOMING
                else -> ConnectionState.OUTGOING
            }
            val item = ConnectionItem(
                id = r[CONNECTIONS.ID]!!,
                person = person(r, state, r[CONNECTIONS.ID]),
                since = (if (accepted) r[CONNECTIONS.ANSWERED_AT] else r[CONNECTIONS.CREATED_AT])
                    ?.toInstant() ?: r[CONNECTIONS.CREATED_AT]!!.toInstant(),
            )
            when (state) {
                ConnectionState.CONNECTED -> connected += item
                ConnectionState.INCOMING -> incoming += item
                else -> outgoing += item
            }
        }
        return ConnectionsResponse(connected, incoming, outgoing)
    }

    fun search(userId: UUID, q: String): PeopleResponse {
        val needle = q.trim()
        if (needle.length < MIN_QUERY) return PeopleResponse(emptyList())
        val standing = DSL.field(
            DSL.select(CONNECTIONS.ID).from(CONNECTIONS)
                .where(pairIs(userId, USERS.ID))
                .limit(1)
        )
        val rows = dsl.select(
            USERS.ID, USERS.HANDLE, USERS.DISPLAY_NAME, USERS.AVATAR_SEED, USERS.CREATED_AT,
        )
            .from(USERS)
            .where(
                USERS.ID.ne(userId),
                DSL.condition("{0}::text ILIKE {1}", USERS.HANDLE, DSL.`val`("%$needle%"))
                    .or(USERS.DISPLAY_NAME.likeIgnoreCase("%$needle%")),
            )
            .orderBy(
                DSL.condition("{0}::text = {1}", USERS.HANDLE, DSL.`val`(needle.lowercase())).desc(),
                USERS.DISPLAY_NAME.asc(),
            )
            .limit(SEARCH_LIMIT)
            .fetch()

        val standings = standingsWith(userId, rows.map { it[USERS.ID]!! })
        return PeopleResponse(
            rows.map { r ->
                val found = standings[r[USERS.ID]!!]
                person(r, found?.first ?: ConnectionState.NONE, found?.second)
            },
        )
    }

    fun profile(viewerId: UUID?, userId: UUID): PersonCard {
        val row = dsl.select(USERS.ID, USERS.HANDLE, USERS.DISPLAY_NAME, USERS.AVATAR_SEED, USERS.CREATED_AT)
            .from(USERS)
            .where(USERS.ID.eq(userId))
            .fetchOne()
            ?: throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)
        val standing = if (viewerId == null || viewerId == userId) {
            null
        } else {
            standingsWith(viewerId, listOf(userId))[userId]
        }
        return person(row, standing?.first ?: ConnectionState.NONE, standing?.second)
    }

    @Transactional
    fun request(requesterId: UUID, addresseeId: UUID): ConnectionResponse {
        if (requesterId == addresseeId) {
            throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.VALIDATION_FAILED)
        }
        dsl.select(USERS.DISPLAY_NAME, USERS.HANDLE).from(USERS)
            .where(USERS.ID.eq(addresseeId))
            .fetchOne()
            ?: throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)

        val existing = dsl.select(CONNECTIONS.ID, CONNECTIONS.STATUS, CONNECTIONS.REQUESTER_ID)
            .from(CONNECTIONS)
            .where(pairIs(requesterId, DSL.`val`(addresseeId)))
            .fetchOne()

        val id = when {
            existing == null -> insert(requesterId, addresseeId)
            existing[CONNECTIONS.STATUS] == ConnectionStatus.declined ->
                reopen(existing[CONNECTIONS.ID]!!, requesterId, addresseeId)
            existing[CONNECTIONS.STATUS] == ConnectionStatus.pending &&
                existing[CONNECTIONS.REQUESTER_ID] != requesterId ->
                return accept(existing[CONNECTIONS.ID]!!, requesterId)
            else -> throw ApiException(HttpStatus.CONFLICT, ProblemCode.ALREADY_CONNECTED)
        }

        notifications.create(
            addresseeId,
            NotificationKind.CONNECTION_REQUESTED,
            mapOf("connectionId" to id.toString(), "senderName" to requesterName(requesterId)),
        )
        return ConnectionResponse(id, ConnectionState.OUTGOING)
    }

    @Transactional
    fun accept(connectionId: UUID, userId: UUID): ConnectionResponse {
        val row = pending(connectionId, userId)
        dsl.update(CONNECTIONS)
            .set(CONNECTIONS.STATUS, ConnectionStatus.accepted)
            .set(CONNECTIONS.ANSWERED_AT, OffsetDateTime.now(clock))
            .where(CONNECTIONS.ID.eq(connectionId))
            .execute()
        notifications.create(
            row[CONNECTIONS.REQUESTER_ID]!!,
            NotificationKind.CONNECTION_ACCEPTED,
            mapOf("connectionId" to connectionId.toString(), "senderName" to requesterName(userId)),
        )
        return ConnectionResponse(connectionId, ConnectionState.CONNECTED)
    }

    @Transactional
    fun decline(connectionId: UUID, userId: UUID): ConnectionResponse {
        pending(connectionId, userId)
        dsl.update(CONNECTIONS)
            .set(CONNECTIONS.STATUS, ConnectionStatus.declined)
            .set(CONNECTIONS.ANSWERED_AT, OffsetDateTime.now(clock))
            .where(CONNECTIONS.ID.eq(connectionId))
            .execute()
        notifications.clearForConnection(userId, connectionId)
        return ConnectionResponse(connectionId, ConnectionState.NONE)
    }

    fun areConnected(one: UUID, other: UUID): Boolean =
        dsl.fetchExists(
            CONNECTIONS,
            pairIs(one, DSL.`val`(other)).and(CONNECTIONS.STATUS.eq(ConnectionStatus.accepted)),
        )

    private fun insert(requesterId: UUID, addresseeId: UUID): UUID =
        try {
            dsl.insertInto(CONNECTIONS)
                .set(CONNECTIONS.REQUESTER_ID, requesterId)
                .set(CONNECTIONS.ADDRESSEE_ID, addresseeId)
                .returning(CONNECTIONS.ID)
                .fetchOne(CONNECTIONS.ID)!!
        } catch (raced: DuplicateKeyException) {
            throw ApiException(HttpStatus.CONFLICT, ProblemCode.ALREADY_CONNECTED)
        }

    private fun reopen(connectionId: UUID, requesterId: UUID, addresseeId: UUID): UUID {
        dsl.update(CONNECTIONS)
            .set(CONNECTIONS.STATUS, ConnectionStatus.pending)
            .set(CONNECTIONS.REQUESTER_ID, requesterId)
            .set(CONNECTIONS.ADDRESSEE_ID, addresseeId)
            .set(CONNECTIONS.CREATED_AT, OffsetDateTime.now(clock))
            .setNull(CONNECTIONS.ANSWERED_AT)
            .where(CONNECTIONS.ID.eq(connectionId))
            .execute()
        return connectionId
    }

    private fun pending(connectionId: UUID, userId: UUID): Record {
        val row = dsl.select(CONNECTIONS.REQUESTER_ID, CONNECTIONS.ADDRESSEE_ID, CONNECTIONS.STATUS)
            .from(CONNECTIONS)
            .where(CONNECTIONS.ID.eq(connectionId), CONNECTIONS.ADDRESSEE_ID.eq(userId))
            .fetchOne()
            ?: throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)
        if (row[CONNECTIONS.STATUS] != ConnectionStatus.pending) {
            throw ApiException(HttpStatus.CONFLICT, ProblemCode.INVALID_STATUS)
        }
        return row
    }

    private fun standingsWith(userId: UUID, others: List<UUID>): Map<UUID, Pair<ConnectionState, UUID>> {
        if (others.isEmpty()) return emptyMap()
        return dsl.select(CONNECTIONS.ID, CONNECTIONS.STATUS, CONNECTIONS.REQUESTER_ID, CONNECTIONS.ADDRESSEE_ID)
            .from(CONNECTIONS)
            .where(
                mine(userId),
                CONNECTIONS.REQUESTER_ID.`in`(others).or(CONNECTIONS.ADDRESSEE_ID.`in`(others)),
                CONNECTIONS.STATUS.ne(ConnectionStatus.declined),
            )
            .fetch()
            .associate { r ->
                val requester = r[CONNECTIONS.REQUESTER_ID]!!
                val other = if (requester == userId) r[CONNECTIONS.ADDRESSEE_ID]!! else requester
                val state = when {
                    r[CONNECTIONS.STATUS] == ConnectionStatus.accepted -> ConnectionState.CONNECTED
                    requester == userId -> ConnectionState.OUTGOING
                    else -> ConnectionState.INCOMING
                }
                other to (state to r[CONNECTIONS.ID]!!)
            }
    }

    private fun requesterName(userId: UUID): String =
        dsl.select(USERS.DISPLAY_NAME).from(USERS).where(USERS.ID.eq(userId))
            .fetchOne(USERS.DISPLAY_NAME) ?: ""

    private fun person(r: Record, state: ConnectionState, connectionId: UUID?) = PersonCard(
        id = r[USERS.ID]!!,
        handle = r[USERS.HANDLE]!!,
        displayName = r[USERS.DISPLAY_NAME]!!,
        avatarSeed = r[USERS.AVATAR_SEED]!!,
        memberSince = r[USERS.CREATED_AT]!!.toInstant(),
        state = state,
        connectionId = connectionId,
    )

    private fun mine(userId: UUID): Condition =
        CONNECTIONS.REQUESTER_ID.eq(userId).or(CONNECTIONS.ADDRESSEE_ID.eq(userId))

    private fun otherSide(userId: UUID) =
        DSL.`when`(CONNECTIONS.REQUESTER_ID.eq(userId), CONNECTIONS.ADDRESSEE_ID)
            .otherwise(CONNECTIONS.REQUESTER_ID)

    private fun pairIs(one: UUID, other: org.jooq.Field<UUID?>): Condition =
        CONNECTIONS.REQUESTER_ID.eq(one).and(CONNECTIONS.ADDRESSEE_ID.eq(other))
            .or(CONNECTIONS.REQUESTER_ID.eq(other).and(CONNECTIONS.ADDRESSEE_ID.eq(one)))
}
