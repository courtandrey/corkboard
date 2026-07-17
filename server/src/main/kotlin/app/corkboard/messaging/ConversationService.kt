package app.corkboard.messaging

import app.corkboard.common.ApiException
import app.corkboard.common.Cursors
import app.corkboard.common.ProblemCode
import app.corkboard.events.AuthorCard
import app.corkboard.events.EventSnippet
import app.corkboard.events.EventStatus
import app.corkboard.jooq.tables.records.MessagesRecord
import app.corkboard.jooq.tables.references.APPLICATIONS
import app.corkboard.jooq.tables.references.CONVERSATIONS
import app.corkboard.jooq.tables.references.EVENTS
import app.corkboard.jooq.tables.references.MESSAGES
import app.corkboard.jooq.tables.references.USERS
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class Participants(val ownerId: UUID, val applicantId: UUID) {
    fun otherThan(userId: UUID): UUID = if (userId == ownerId) applicantId else ownerId
    fun includes(userId: UUID): Boolean = userId == ownerId || userId == applicantId
}

@Service
class ConversationService(
    private val dsl: DSLContext,
    private val publisher: ApplicationEventPublisher,
    private val clock: Clock,
) {

    fun requireParticipants(conversationId: UUID, userId: UUID): Participants {
        val row = dsl.select(CONVERSATIONS.OWNER_ID, CONVERSATIONS.APPLICANT_ID)
            .from(CONVERSATIONS)
            .where(CONVERSATIONS.ID.eq(conversationId))
            .fetchOne()
            ?: throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)
        val participants = Participants(row[CONVERSATIONS.OWNER_ID]!!, row[CONVERSATIONS.APPLICANT_ID]!!)
        if (!participants.includes(userId)) {
            throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)
        }
        return participants
    }

    fun list(userId: UUID, cursor: String?, limit: Int): ConversationListResponse {
        val owner = USERS.`as`("owner_user")
        val applicant = USERS.`as`("applicant_user")

        var cond = CONVERSATIONS.OWNER_ID.eq(userId).or(CONVERSATIONS.APPLICANT_ID.eq(userId))
        Cursors.decode(cursor ?: "")?.let { (at, id) ->
            cond = cond.and(DSL.row(CONVERSATIONS.LAST_MESSAGE_AT, CONVERSATIONS.ID).lessThan(at, id))
        }

        val unread = DSL.field(
            DSL.selectCount().from(MESSAGES).where(
                MESSAGES.CONVERSATION_ID.eq(CONVERSATIONS.ID),
                MESSAGES.SENDER_ID.ne(userId),
                MESSAGES.READ_AT.isNull,
            )
        )
        val lastBody = DSL.field(
            DSL.select(MESSAGES.BODY).from(MESSAGES)
                .where(MESSAGES.CONVERSATION_ID.eq(CONVERSATIONS.ID))
                .orderBy(MESSAGES.CREATED_AT.desc(), MESSAGES.ID.desc())
                .limit(1)
        )

        val rows = dsl.select(
            CONVERSATIONS.ID, CONVERSATIONS.OWNER_ID, CONVERSATIONS.APPLICANT_ID,
            CONVERSATIONS.APPLICATION_ID, CONVERSATIONS.LAST_MESSAGE_AT,
            EVENTS.ID, EVENTS.TITLE, EVENTS.STATUS,
            APPLICATIONS.STATUS,
            owner.DISPLAY_NAME, owner.AVATAR_SEED, owner.CREATED_AT,
            applicant.DISPLAY_NAME, applicant.AVATAR_SEED, applicant.CREATED_AT,
            unread, lastBody,
        )
            .from(CONVERSATIONS)
            .join(EVENTS).on(EVENTS.ID.eq(CONVERSATIONS.EVENT_ID))
            .join(APPLICATIONS).on(APPLICATIONS.ID.eq(CONVERSATIONS.APPLICATION_ID))
            .join(owner).on(owner.ID.eq(CONVERSATIONS.OWNER_ID))
            .join(applicant).on(applicant.ID.eq(CONVERSATIONS.APPLICANT_ID))
            .where(cond)
            .orderBy(CONVERSATIONS.LAST_MESSAGE_AT.desc(), CONVERSATIONS.ID.desc())
            .limit(limit + 1)
            .fetch()

        val page = rows.take(limit)
        val nextCursor = if (rows.size > limit) {
            page.last().let { Cursors.encode(it[CONVERSATIONS.LAST_MESSAGE_AT]!!, it[CONVERSATIONS.ID]!!) }
        } else null

        val items = page.map { r ->
            val amOwner = r[CONVERSATIONS.OWNER_ID] == userId
            val other = if (amOwner) applicant else owner
            ConversationSummary(
                id = r[CONVERSATIONS.ID]!!,
                event = EventSnippet(
                    id = r[EVENTS.ID]!!,
                    title = r[EVENTS.TITLE]!!,
                    status = EventStatus.fromDb(r[EVENTS.STATUS]!!.literal),
                ),
                otherParty = AuthorCard(
                    displayName = r[other.DISPLAY_NAME]!!,
                    avatarSeed = r[other.AVATAR_SEED]!!,
                    memberSince = r[other.CREATED_AT]!!.toInstant(),
                ),
                applicationId = r[CONVERSATIONS.APPLICATION_ID]!!,
                applicationStatus = ApplicationStatus.fromDb(r[APPLICATIONS.STATUS]!!.literal),
                lastMessageAt = r[CONVERSATIONS.LAST_MESSAGE_AT]!!.toInstant(),
                lastMessageBody = r[lastBody],
                unreadCount = r[unread]!!,
            )
        }
        return ConversationListResponse(items, nextCursor)
    }

    fun messages(conversationId: UUID, userId: UUID, cursor: String?, limit: Int): MessageListResponse {
        requireParticipants(conversationId, userId)
        var cond = MESSAGES.CONVERSATION_ID.eq(conversationId)
        Cursors.decode(cursor ?: "")?.let { (at, id) ->
            cond = cond.and(DSL.row(MESSAGES.CREATED_AT, MESSAGES.ID).lessThan(at, id))
        }
        val rows = dsl.selectFrom(MESSAGES)
            .where(cond)
            .orderBy(MESSAGES.CREATED_AT.desc(), MESSAGES.ID.desc())
            .limit(limit + 1)
            .fetch()
        val page = rows.take(limit)
        val nextCursor = if (rows.size > limit) {
            page.last().let { Cursors.encode(it.createdAt!!, it.id!!) }
        } else null
        return MessageListResponse(page.reversed().map(::toMessage), nextCursor)
    }

    @Transactional
    fun send(conversationId: UUID, senderId: UUID, body: String): MessageResponse {
        val participants = requireParticipants(conversationId, senderId)
        val trimmed = body.trim()
        if (trimmed.isEmpty()) {
            throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.VALIDATION_FAILED)
        }
        val message = insertMessage(conversationId, senderId, trimmed)
        publisher.publishEvent(MessageCreated(participants.otherThan(senderId), conversationId, message))
        return message
    }

    fun insertMessage(conversationId: UUID, senderId: UUID, body: String): MessageResponse {
        val record = dsl.insertInto(MESSAGES)
            .set(MESSAGES.CONVERSATION_ID, conversationId)
            .set(MESSAGES.SENDER_ID, senderId)
            .set(MESSAGES.BODY, body)
            .returning()
            .fetchOne()!!
        dsl.update(CONVERSATIONS)
            .set(CONVERSATIONS.LAST_MESSAGE_AT, record.createdAt)
            .where(CONVERSATIONS.ID.eq(conversationId))
            .execute()
        return toMessage(record)
    }

    @Transactional
    fun markRead(conversationId: UUID, readerId: UUID) {
        val participants = requireParticipants(conversationId, readerId)
        val updated = dsl.update(MESSAGES)
            .set(MESSAGES.READ_AT, OffsetDateTime.now(clock))
            .where(
                MESSAGES.CONVERSATION_ID.eq(conversationId),
                MESSAGES.SENDER_ID.ne(readerId),
                MESSAGES.READ_AT.isNull,
            )
            .execute()
        if (updated > 0) {
            publisher.publishEvent(ConversationRead(participants.otherThan(readerId), conversationId))
        }
    }

    private fun toMessage(record: MessagesRecord): MessageResponse =
        MessageResponse(
            id = record.id!!,
            conversationId = record.conversationId!!,
            senderId = record.senderId!!,
            body = record.body!!,
            createdAt = record.createdAt!!.toInstant(),
            readAt = record.readAt?.toInstant(),
        )
}
