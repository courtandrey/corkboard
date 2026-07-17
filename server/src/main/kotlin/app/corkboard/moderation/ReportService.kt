package app.corkboard.moderation

import app.corkboard.common.ApiException
import app.corkboard.common.ProblemCode
import app.corkboard.jooq.enums.EventStatus as DbEventStatus
import app.corkboard.jooq.enums.NotificationKind
import app.corkboard.jooq.enums.ReportReason as DbReportReason
import app.corkboard.jooq.tables.references.EVENTS
import app.corkboard.jooq.tables.references.NOTIFICATIONS
import app.corkboard.jooq.tables.references.REPORTS
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReportService(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
) {

    @Transactional
    fun report(eventId: UUID, reporterId: UUID, req: ReportRequest) {
        val event = dsl.select(EVENTS.AUTHOR_ID, EVENTS.TITLE)
            .from(EVENTS).where(EVENTS.ID.eq(eventId)).fetchOne()
            ?: throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)
        val authorId = event[EVENTS.AUTHOR_ID]!!
        if (authorId == reporterId) {
            throw ApiException(HttpStatus.CONFLICT, ProblemCode.OWN_EVENT)
        }

        dsl.insertInto(REPORTS)
            .set(REPORTS.EVENT_ID, eventId)
            .set(REPORTS.REPORTER_ID, reporterId)
            .set(REPORTS.REASON, DbReportReason.valueOf(req.reason.key))
            .set(REPORTS.DETAIL, req.detail?.trim()?.takeIf { it.isNotEmpty() })
            .onConflictDoNothing()
            .execute()

        val status = dsl.select(EVENTS.STATUS).from(EVENTS)
            .where(EVENTS.ID.eq(eventId)).fetchOne(EVENTS.STATUS)
        if (status == DbEventStatus.under_review) {
            notifyAuthorOnce(eventId, authorId, event[EVENTS.TITLE]!!)
        }
    }

    private fun notifyAuthorOnce(eventId: UUID, authorId: UUID, title: String) {
        val alreadyNotified = dsl.fetchExists(
            DSL.selectOne().from(NOTIFICATIONS).where(
                NOTIFICATIONS.USER_ID.eq(authorId),
                NOTIFICATIONS.KIND.eq(NotificationKind.event_under_review),
                DSL.condition("{0}->>'eventId' = {1}", NOTIFICATIONS.PAYLOAD, DSL.`val`(eventId.toString())),
            )
        )
        if (alreadyNotified) return
        val payload = objectMapper.writeValueAsString(
            mapOf("eventId" to eventId.toString(), "eventTitle" to title)
        )
        dsl.insertInto(NOTIFICATIONS)
            .set(NOTIFICATIONS.USER_ID, authorId)
            .set(NOTIFICATIONS.KIND, NotificationKind.event_under_review)
            .set(NOTIFICATIONS.PAYLOAD, JSONB.valueOf(payload))
            .execute()
    }
}
