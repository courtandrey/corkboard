package app.corkboard.notifications

import app.corkboard.common.Cursors
import app.corkboard.jooq.enums.NotificationKind as DbNotificationKind
import app.corkboard.jooq.tables.records.NotificationsRecord
import app.corkboard.jooq.tables.references.NOTIFICATIONS
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.Condition
import org.jooq.JSONB
import org.jooq.impl.DSL
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

enum class NotificationKind(@get:JsonValue val key: String) {
    APPLICATION_RECEIVED("application_received"),
    APPLICATION_STATUS("application_status"),
    MESSAGE_RECEIVED("message_received"),
    EVENT_EXPIRING("event_expiring"),
    EVENT_UNDER_REVIEW("event_under_review");

    companion object {
        fun fromDb(literal: String): NotificationKind = entries.first { it.key == literal }
    }
}

data class NotificationResponse(
    val id: UUID,
    val kind: NotificationKind,
    val payload: Map<String, Any?>,
    val createdAt: Instant,
)

data class NotificationListResponse(
    val items: List<NotificationResponse>,
    val unreadCount: Int,
    val nextCursor: String?,
)

data class NotificationCreated(val userId: UUID, val notification: NotificationResponse)

@Service
class NotificationService(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
    private val publisher: ApplicationEventPublisher,
) {

    fun create(userId: UUID, kind: NotificationKind, payload: Map<String, Any?>): NotificationResponse {
        val record = dsl.insertInto(NOTIFICATIONS)
            .set(NOTIFICATIONS.USER_ID, userId)
            .set(NOTIFICATIONS.KIND, DbNotificationKind.valueOf(kind.key))
            .set(NOTIFICATIONS.PAYLOAD, JSONB.valueOf(objectMapper.writeValueAsString(payload)))
            .returning()
            .fetchOne()!!
        val response = toResponse(record)
        publisher.publishEvent(NotificationCreated(userId, response))
        return response
    }

    fun list(userId: UUID, cursor: String?, limit: Int): NotificationListResponse {
        var cond = NOTIFICATIONS.USER_ID.eq(userId)
        Cursors.decode(cursor ?: "")?.let { (at, id) ->
            cond = cond.and(DSL.row(NOTIFICATIONS.CREATED_AT, NOTIFICATIONS.ID).lessThan(at, id))
        }
        val rows = dsl.selectFrom(NOTIFICATIONS)
            .where(cond)
            .orderBy(NOTIFICATIONS.CREATED_AT.desc(), NOTIFICATIONS.ID.desc())
            .limit(limit + 1)
            .fetch()
        val page = rows.take(limit)
        val nextCursor = if (rows.size > limit) {
            page.last().let { Cursors.encode(it.createdAt!!, it.id!!) }
        } else null
        val unread = dsl.fetchCount(NOTIFICATIONS, NOTIFICATIONS.USER_ID.eq(userId))
        return NotificationListResponse(page.map(::toResponse), unread, nextCursor)
    }

    fun dismiss(userId: UUID, ids: List<UUID>?) {
        var cond = NOTIFICATIONS.USER_ID.eq(userId)
        if (ids != null) cond = cond.and(NOTIFICATIONS.ID.`in`(ids))
        dsl.deleteFrom(NOTIFICATIONS).where(cond).execute()
    }

    fun hasPendingForConversation(userId: UUID, conversationId: UUID): Boolean =
        dsl.fetchExists(
            NOTIFICATIONS,
            NOTIFICATIONS.USER_ID.eq(userId),
            NOTIFICATIONS.KIND.eq(DbNotificationKind.message_received),
            conversationIs(conversationId),
        )

    fun clearForConversation(userId: UUID, conversationId: UUID) {
        dsl.deleteFrom(NOTIFICATIONS)
            .where(NOTIFICATIONS.USER_ID.eq(userId), conversationIs(conversationId))
            .execute()
    }

    private fun conversationIs(conversationId: UUID): Condition =
        DSL.condition(
            "{0}->>'conversationId' = {1}",
            NOTIFICATIONS.PAYLOAD,
            DSL.`val`(conversationId.toString()),
        )

    private fun toResponse(record: NotificationsRecord): NotificationResponse =
        NotificationResponse(
            id = record.id!!,
            kind = NotificationKind.fromDb(record.kind!!.literal),
            payload = objectMapper.readValue(record.payload!!.data(), Map::class.java)
                .entries.associate { (k, v) -> k.toString() to v },
            createdAt = record.createdAt!!.toInstant(),
        )
}
