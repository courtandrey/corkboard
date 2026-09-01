package app.corkboard.events

import app.corkboard.common.ApiException
import app.corkboard.common.ProblemCode
import app.corkboard.jooq.enums.EventStatus as DbEventStatus
import app.corkboard.jooq.enums.EventType as DbEventType
import app.corkboard.jooq.tables.references.APPLICATIONS
import app.corkboard.jooq.tables.references.EVENTS
import app.corkboard.jooq.tables.references.EVENT_HIDES
import app.corkboard.jooq.tables.references.SCOPES
import app.corkboard.jooq.tables.references.USERS
import app.corkboard.jooq.tables.references.VOTES
import app.corkboard.meta.EventType
import app.corkboard.scopes.ScopeKind
import app.corkboard.scopes.ScopeService
import app.corkboard.tags.TagService
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Geometry
import org.jooq.impl.DSL
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EventService(
    private val dsl: DSLContext,
    private val tags: TagService,
    private val scopes: ScopeService,
    private val clock: Clock,
) {

    companion object {
        private val HIDDEN_STATUSES =
            setOf(DbEventStatus.removed, DbEventStatus.taken_down, DbEventStatus.under_review)
        private val LNG = DSL.field("ST_X({0})", Double::class.java, EVENTS.LOCATION)
        private val LAT = DSL.field("ST_Y({0})", Double::class.java, EVENTS.LOCATION)
    }

    private data class EventRow(
        val id: UUID,
        val authorId: UUID,
        val scopeId: UUID,
        val boardOwnerId: UUID?,
        val type: DbEventType,
        val status: DbEventStatus,
        val title: String,
        val body: String,
        val lng: Double,
        val lat: Double,
        val applyable: Boolean,
        val score: Int,
        val applicationCount: Int,
        val expiresAt: OffsetDateTime?,
        val resolvedAt: OffsetDateTime?,
        val createdAt: OffsetDateTime,
        val updatedAt: OffsetDateTime,
    )

    @Transactional
    fun create(authorId: UUID, scopeId: UUID, req: CreateEventRequest): EventDetail {
        val title = req.title.trim()
        val body = req.body.trim()
        if (title.length < 3 || body.isEmpty()) {
            throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.VALIDATION_FAILED)
        }
        val kind = scopes.kindOf(scopeId)
        scopes.requireAllowsType(kind, req.type.key)
        val id = dsl.insertInto(EVENTS)
            .set(EVENTS.AUTHOR_ID, authorId)
            .set(EVENTS.SCOPE_ID, scopeId)
            .set(EVENTS.TYPE, DbEventType.valueOf(req.type.key))
            .set(EVENTS.TITLE, title)
            .set(EVENTS.BODY, body)
            .set(EVENTS.LOCATION, point(req.location))
            .set(EVENTS.APPLYABLE, req.applyable && kind == ScopeKind.GLOBAL)
            .set(EVENTS.EXPIRES_AT, req.expiresAt?.atOffset(ZoneOffset.UTC))
            .returning(EVENTS.ID)
            .fetchOne(EVENTS.ID)!!

        if (req.tags.isNotEmpty()) {
            tags.replaceEventTags(id, req.tags)
        }
        return detail(id, authorId, scopeId)
    }

    fun detail(id: UUID, viewerId: UUID?, scopeId: UUID): EventDetail {
        val event = fetchEvent(id, scopeId)
        if (event.status in HIDDEN_STATUSES && event.authorId != viewerId) {
            throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)
        }
        return toDetail(event, viewerId)
    }

    fun detailAcross(id: UUID, viewerId: UUID, scopeIds: List<UUID>): EventDetail {
        val event = fetchEvent(id, scopeIds)
        if (event.status in HIDDEN_STATUSES && event.authorId != viewerId) {
            throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)
        }
        return toDetail(event, viewerId)
    }

    @Transactional
    fun update(id: UUID, viewerId: UUID, scopeId: UUID, req: UpdateEventRequest): EventDetail {
        val event = fetchAuthored(id, viewerId, scopeId)
        if ((req.type != null || req.location != null) && event.applicationCount > 0) {
            throw ApiException(HttpStatus.CONFLICT, ProblemCode.EDIT_LOCKED)
        }
        req.type?.let { scopes.requireAllowsType(scopes.kindOf(event.scopeId), it.key) }
        val title = req.title?.trim()
        val body = req.body?.trim()
        if (title != null && title.length < 3 || body != null && body.isEmpty()) {
            throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.VALIDATION_FAILED)
        }

        val update = dsl.update(EVENTS).set(EVENTS.UPDATED_AT, OffsetDateTime.now(clock))
        req.type?.let { update.set(EVENTS.TYPE, DbEventType.valueOf(it.key)) }
        title?.let { update.set(EVENTS.TITLE, it) }
        body?.let { update.set(EVENTS.BODY, it) }
        req.location?.let { update.set(EVENTS.LOCATION, point(it)) }
        req.applyable?.let { update.set(EVENTS.APPLYABLE, it && scopes.isGlobal(event.scopeId)) }
        if (req.neverExpires == true) {
            update.setNull(EVENTS.EXPIRES_AT)
            update.setNull(EVENTS.EXPIRING_NOTIFIED_AT)
        } else req.expiresAt?.let {
            update.set(EVENTS.EXPIRES_AT, it.atOffset(ZoneOffset.UTC))
            update.setNull(EVENTS.EXPIRING_NOTIFIED_AT)
        }
        update.where(EVENTS.ID.eq(id)).execute()

        req.tags?.let { tags.replaceEventTags(id, it) }
        return detail(id, viewerId, scopeId)
    }

    @Transactional
    fun resolve(id: UUID, viewerId: UUID, scopeId: UUID): EventDetail {
        val event = fetchAuthored(id, viewerId, scopeId)
        if (event.status != DbEventStatus.active) {
            throw ApiException(HttpStatus.CONFLICT, ProblemCode.INVALID_STATUS)
        }
        dsl.update(EVENTS)
            .set(EVENTS.STATUS, DbEventStatus.resolved)
            .set(EVENTS.RESOLVED_AT, OffsetDateTime.now(clock))
            .where(EVENTS.ID.eq(id))
            .execute()
        return detail(id, viewerId, scopeId)
    }

    @Transactional
    fun renew(id: UUID, viewerId: UUID, scopeId: UUID, expiresAt: Instant): EventDetail {
        val event = fetchAuthored(id, viewerId, scopeId)
        if (event.status != DbEventStatus.active && event.status != DbEventStatus.expired) {
            throw ApiException(HttpStatus.CONFLICT, ProblemCode.INVALID_STATUS)
        }
        dsl.update(EVENTS)
            .set(EVENTS.STATUS, DbEventStatus.active)
            .set(EVENTS.EXPIRES_AT, expiresAt.atOffset(ZoneOffset.UTC))
            .setNull(EVENTS.EXPIRING_NOTIFIED_AT)
            .where(EVENTS.ID.eq(id))
            .execute()
        return detail(id, viewerId, scopeId)
    }

    @Transactional
    fun remove(id: UUID, viewerId: UUID, scopeId: UUID) {
        fetchAuthored(id, viewerId, scopeId)
        dsl.update(EVENTS)
            .set(EVENTS.STATUS, DbEventStatus.removed)
            .where(EVENTS.ID.eq(id))
            .execute()
    }

    fun myEvents(userId: UUID, status: EventStatus?, cursor: String?, limit: Int): MyEventsResponse {
        var cond = EVENTS.AUTHOR_ID.eq(userId)
        if (!scopes.enabled()) cond = cond.and(EVENTS.SCOPE_ID.eq(scopes.globalId))
        status?.let { cond = cond.and(EVENTS.STATUS.eq(DbEventStatus.valueOf(it.key))) }
        app.corkboard.common.Cursors.decode(cursor ?: "")?.let { (at, cursorId) ->
            cond = cond.and(DSL.row(EVENTS.CREATED_AT, EVENTS.ID).lessThan(at, cursorId))
        }
        val rows = dsl.select(
            EVENTS.ID, EVENTS.SCOPE_ID, EVENTS.TYPE, EVENTS.STATUS, EVENTS.TITLE, LNG, LAT,
            EVENTS.APPLYABLE, EVENTS.SCORE, EVENTS.APPLICATION_COUNT,
            EVENTS.EXPIRES_AT, EVENTS.RESOLVED_AT, EVENTS.CREATED_AT, EVENTS.UPDATED_AT,
        )
            .from(EVENTS)
            .where(cond)
            .orderBy(EVENTS.CREATED_AT.desc(), EVENTS.ID.desc())
            .limit(limit + 1)
            .fetch()
        val page = rows.take(limit)
        val nextCursor = if (rows.size > limit) {
            page.last().let { app.corkboard.common.Cursors.encode(it[EVENTS.CREATED_AT]!!, it[EVENTS.ID]!!) }
        } else null
        val items = page.map { r ->
            MyEventItem(
                id = r[EVENTS.ID]!!,
                scope = scopes.kindOf(r[EVENTS.SCOPE_ID]!!),
                boardOwnerId = if (scopes.isGlobal(r[EVENTS.SCOPE_ID]!!)) null else userId,
                type = EventType.fromKey(r[EVENTS.TYPE]!!.literal)!!,
                status = EventStatus.fromDb(r[EVENTS.STATUS]!!.literal),
                title = r[EVENTS.TITLE]!!,
                location = LatLng(r[LNG]!!, r[LAT]!!),
                applyable = r[EVENTS.APPLYABLE]!!,
                score = r[EVENTS.SCORE]!!,
                applicationCount = r[EVENTS.APPLICATION_COUNT]!!,
                expiresAt = r[EVENTS.EXPIRES_AT]?.toInstant(),
                resolvedAt = r[EVENTS.RESOLVED_AT]?.toInstant(),
                createdAt = r[EVENTS.CREATED_AT]!!.toInstant(),
                updatedAt = r[EVENTS.UPDATED_AT]!!.toInstant(),
            )
        }
        return MyEventsResponse(items, nextCursor)
    }

    private fun fetchAuthored(id: UUID, viewerId: UUID, scopeId: UUID): EventRow {
        val event = fetchEvent(id, scopeId)
        if (event.status in HIDDEN_STATUSES && event.authorId != viewerId) {
            throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)
        }
        if (event.authorId != viewerId) {
            throw ApiException(HttpStatus.FORBIDDEN, ProblemCode.FORBIDDEN)
        }
        return event
    }

    fun requireSharedBoardAuthor(id: UUID, viewerId: UUID?): UUID {
        val row = dsl.select(EVENTS.AUTHOR_ID, EVENTS.STATUS)
            .from(EVENTS)
            .where(EVENTS.ID.eq(id), EVENTS.SCOPE_ID.eq(scopes.globalId))
            .fetchOne()
            ?: throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)
        if (row[EVENTS.STATUS] in HIDDEN_STATUSES && row[EVENTS.AUTHOR_ID] != viewerId) {
            throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)
        }
        return row[EVENTS.AUTHOR_ID]!!
    }

    private fun fetchEvent(id: UUID, scopeId: UUID): EventRow = fetchEvent(id, listOf(scopeId))

    private fun fetchEvent(id: UUID, scopeIds: List<UUID>): EventRow =
        dsl.select(
            EVENTS.ID, EVENTS.AUTHOR_ID, EVENTS.SCOPE_ID, SCOPES.OWNER_ID,
            EVENTS.TYPE, EVENTS.STATUS, EVENTS.TITLE, EVENTS.BODY,
            LNG, LAT, EVENTS.APPLYABLE, EVENTS.SCORE, EVENTS.APPLICATION_COUNT,
            EVENTS.EXPIRES_AT, EVENTS.RESOLVED_AT, EVENTS.CREATED_AT, EVENTS.UPDATED_AT,
        )
            .from(EVENTS)
            .join(SCOPES).on(SCOPES.ID.eq(EVENTS.SCOPE_ID))
            .where(EVENTS.ID.eq(id), EVENTS.SCOPE_ID.`in`(scopeIds))
            .fetchOne { r ->
                EventRow(
                    id = r[EVENTS.ID]!!,
                    authorId = r[EVENTS.AUTHOR_ID]!!,
                    scopeId = r[EVENTS.SCOPE_ID]!!,
                    boardOwnerId = r[SCOPES.OWNER_ID],
                    type = r[EVENTS.TYPE]!!,
                    status = r[EVENTS.STATUS]!!,
                    title = r[EVENTS.TITLE]!!,
                    body = r[EVENTS.BODY]!!,
                    lng = r[LNG]!!,
                    lat = r[LAT]!!,
                    applyable = r[EVENTS.APPLYABLE]!!,
                    score = r[EVENTS.SCORE]!!,
                    applicationCount = r[EVENTS.APPLICATION_COUNT]!!,
                    expiresAt = r[EVENTS.EXPIRES_AT],
                    resolvedAt = r[EVENTS.RESOLVED_AT],
                    createdAt = r[EVENTS.CREATED_AT]!!,
                    updatedAt = r[EVENTS.UPDATED_AT]!!,
                )
            } ?: throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)

    private fun toDetail(event: EventRow, viewerId: UUID?): EventDetail {
        val author = dsl.select(USERS.DISPLAY_NAME, USERS.HANDLE, USERS.AVATAR_SEED, USERS.CREATED_AT)
            .from(USERS).where(USERS.ID.eq(event.authorId)).fetchOne()!!

        return EventDetail(
            id = event.id,
            scope = scopes.kindOf(event.scopeId),
            boardOwnerId = event.boardOwnerId,
            type = EventType.fromKey(event.type.literal)!!,
            status = EventStatus.fromDb(event.status.literal),
            title = event.title,
            body = event.body,
            location = LatLng(event.lng, event.lat),
            applyable = event.applyable,
            score = event.score,
            applicationCount = event.applicationCount,
            tags = tags.eventTags(event.id).map { (name, slug) -> TagRef(name, slug) },
            author = AuthorCard(
                id = event.authorId,
                displayName = author[USERS.DISPLAY_NAME]!!,
                handle = author[USERS.HANDLE]!!,
                avatarSeed = author[USERS.AVATAR_SEED]!!,
                memberSince = author[USERS.CREATED_AT]!!.toInstant(),
            ),
            viewerState = viewerState(event, viewerId),
            expiresAt = event.expiresAt?.toInstant(),
            resolvedAt = event.resolvedAt?.toInstant(),
            createdAt = event.createdAt.toInstant(),
            updatedAt = event.updatedAt.toInstant(),
        )
    }

    private fun viewerState(event: EventRow, viewerId: UUID?): ViewerState {
        if (viewerId == null) return ViewerState(voted = false, hidden = false, applied = false, isAuthor = false)
        val eventId = event.id
        return ViewerState(
            voted = dsl.fetchExists(VOTES, VOTES.USER_ID.eq(viewerId), VOTES.EVENT_ID.eq(eventId)),
            hidden = dsl.fetchExists(EVENT_HIDES, EVENT_HIDES.USER_ID.eq(viewerId), EVENT_HIDES.EVENT_ID.eq(eventId)),
            applied = dsl.fetchExists(APPLICATIONS, APPLICATIONS.APPLICANT_ID.eq(viewerId), APPLICATIONS.EVENT_ID.eq(eventId)),
            isAuthor = viewerId == event.authorId,
            canRespond = canRespond(event, viewerId),
        )
    }

    private fun canRespond(event: EventRow, viewerId: UUID): Boolean {
        if (viewerId == event.authorId || event.status != DbEventStatus.active) return false
        return if (scopes.isGlobal(event.scopeId)) event.applyable else scopes.subscriptionsEnabled()
    }

    @Suppress("UNCHECKED_CAST")
    private fun point(location: LatLng): Field<Geometry?> =
        DSL.field(
            "ST_SetSRID(ST_MakePoint({0}, {1}), 4326)",
            Geometry::class.java,
            DSL.`val`(location.lng), DSL.`val`(location.lat),
        ) as Field<Geometry?>
}
