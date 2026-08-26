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
import app.corkboard.notifications.NotificationKind
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class Participants(val userA: UUID, val userB: UUID) {
    fun otherThan(userId: UUID): UUID = if (userId == userA) userB else userA
    fun includes(userId: UUID): Boolean = userId == userA || userId == userB

    companion object {
        fun of(one: UUID, other: UUID): Participants =
            if (sortsFirst(one, other)) Participants(one, other) else Participants(other, one)

        private fun sortsFirst(one: UUID, other: UUID): Boolean {
            val high = java.lang.Long.compareUnsigned(one.mostSignificantBits, other.mostSignificantBits)
            if (high != 0) return high < 0
            return java.lang.Long.compareUnsigned(one.leastSignificantBits, other.leastSignificantBits) < 0
        }
    }
}

@Service
class ConversationService(
    private val dsl: DSLContext,
    private val publisher: ApplicationEventPublisher,
    private val notifications: app.corkboard.notifications.NotificationService,
    private val clock: Clock,
) {

    fun requireParticipants(conversationId: UUID, userId: UUID): Participants {
        val row = dsl.select(CONVERSATIONS.USER_A_ID, CONVERSATIONS.USER_B_ID)
            .from(CONVERSATIONS)
            .where(CONVERSATIONS.ID.eq(conversationId))
            .fetchOne()
            ?: throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)
        val participants = Participants(row[CONVERSATIONS.USER_A_ID]!!, row[CONVERSATIONS.USER_B_ID]!!)
        if (!participants.includes(userId)) {
            throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)
        }
        return participants
    }

    fun between(one: UUID, other: UUID): UUID {
        val pair = Participants.of(one, other)
        dsl.insertInto(CONVERSATIONS)
            .set(CONVERSATIONS.USER_A_ID, pair.userA)
            .set(CONVERSATIONS.USER_B_ID, pair.userB)
            .onConflictDoNothing()
            .execute()
        return dsl.select(CONVERSATIONS.ID).from(CONVERSATIONS)
            .where(CONVERSATIONS.USER_A_ID.eq(pair.userA), CONVERSATIONS.USER_B_ID.eq(pair.userB))
            .fetchOne(CONVERSATIONS.ID)!!
    }

    fun list(userId: UUID, cursor: String?, limit: Int): ConversationListResponse {
        val userA = USERS.`as`("user_a")
        val userB = USERS.`as`("user_b")

        var cond = CONVERSATIONS.USER_A_ID.eq(userId).or(CONVERSATIONS.USER_B_ID.eq(userId))
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
            CONVERSATIONS.ID, CONVERSATIONS.USER_A_ID, CONVERSATIONS.LAST_MESSAGE_AT,
            userA.DISPLAY_NAME, userA.HANDLE, userA.AVATAR_SEED, userA.CREATED_AT,
            userB.DISPLAY_NAME, userB.HANDLE, userB.AVATAR_SEED, userB.CREATED_AT,
            unread, lastBody,
        )
            .from(CONVERSATIONS)
            .join(userA).on(userA.ID.eq(CONVERSATIONS.USER_A_ID))
            .join(userB).on(userB.ID.eq(CONVERSATIONS.USER_B_ID))
            .where(cond)
            .orderBy(CONVERSATIONS.LAST_MESSAGE_AT.desc(), CONVERSATIONS.ID.desc())
            .limit(limit + 1)
            .fetch()

        val page = rows.take(limit)
        val nextCursor = if (rows.size > limit) {
            page.last().let { Cursors.encode(it[CONVERSATIONS.LAST_MESSAGE_AT]!!, it[CONVERSATIONS.ID]!!) }
        } else null

        val items = page.map { r ->
            val other = if (r[CONVERSATIONS.USER_A_ID] == userId) userB else userA
            ConversationSummary(
                id = r[CONVERSATIONS.ID]!!,
                otherParty = AuthorCard(
                    displayName = r[other.DISPLAY_NAME]!!,
                    handle = r[other.HANDLE]!!,
                    avatarSeed = r[other.AVATAR_SEED]!!,
                    memberSince = r[other.CREATED_AT]!!.toInstant(),
                ),
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
        val rows = dsl.select(MESSAGES.asterisk(), EVENTS.ID, EVENTS.TITLE, EVENTS.STATUS)
            .from(MESSAGES)
            .leftJoin(EVENTS).on(EVENTS.ID.eq(MESSAGES.EVENT_ID))
            .where(cond)
            .orderBy(MESSAGES.CREATED_AT.desc(), MESSAGES.ID.desc())
            .limit(limit + 1)
            .fetch()
        val page = rows.take(limit)
        val nextCursor = if (rows.size > limit) {
            page.last().let { Cursors.encode(it[MESSAGES.CREATED_AT]!!, it[MESSAGES.ID]!!) }
        } else null
        val items = page.reversed().map { r ->
            toMessage(r.into(MESSAGES).into(MessagesRecord::class.java), snippet(r))
        }
        return MessageListResponse(items, nextCursor)
    }

    @Transactional
    fun send(conversationId: UUID, senderId: UUID, body: String): MessageResponse {
        val participants = requireParticipants(conversationId, senderId)
        val trimmed = body.trim()
        if (trimmed.isEmpty()) {
            throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.VALIDATION_FAILED)
        }
        val message = insertMessage(conversationId, senderId, trimmed)
        val recipient = participants.otherThan(senderId)
        notifyRecipient(conversationId, recipient, senderId)
        publisher.publishEvent(MessageCreated(recipient, conversationId, message))
        return message
    }

    fun notifyRecipient(conversationId: UUID, recipient: UUID, senderId: UUID) {
        if (notifications.hasPendingForConversation(recipient, conversationId)) return
        val senderName = dsl.select(USERS.DISPLAY_NAME).from(USERS)
            .where(USERS.ID.eq(senderId))
            .fetchOne(USERS.DISPLAY_NAME)
            ?: return
        notifications.create(
            recipient,
            NotificationKind.MESSAGE_RECEIVED,
            mapOf(
                "conversationId" to conversationId.toString(),
                "senderName" to senderName,
            ),
        )
    }

    fun insertMessage(
        conversationId: UUID,
        senderId: UUID,
        body: String,
        event: EventSnippet? = null,
    ): MessageResponse {
        val record = dsl.insertInto(MESSAGES)
            .set(MESSAGES.CONVERSATION_ID, conversationId)
            .set(MESSAGES.SENDER_ID, senderId)
            .set(MESSAGES.BODY, body)
            .set(MESSAGES.EVENT_ID, event?.id)
            .returning()
            .fetchOne()!!
        dsl.update(CONVERSATIONS)
            .set(CONVERSATIONS.LAST_MESSAGE_AT, record.createdAt)
            .where(CONVERSATIONS.ID.eq(conversationId))
            .execute()
        return toMessage(record, event)
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
        notifications.clearForConversation(readerId, conversationId)
        if (updated > 0) {
            publisher.publishEvent(ConversationRead(participants.otherThan(readerId), conversationId))
        }
    }

    private fun snippet(record: org.jooq.Record): EventSnippet? =
        record[EVENTS.ID]?.let {
            EventSnippet(
                id = it,
                title = record[EVENTS.TITLE]!!,
                status = EventStatus.fromDb(record[EVENTS.STATUS]!!.literal),
            )
        }

    private fun toMessage(record: MessagesRecord, event: EventSnippet?): MessageResponse =
        MessageResponse(
            id = record.id!!,
            conversationId = record.conversationId!!,
            senderId = record.senderId!!,
            body = record.body!!,
            event = event,
            createdAt = record.createdAt!!.toInstant(),
            readAt = record.readAt?.toInstant(),
        )
}
