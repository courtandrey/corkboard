package app.corkboard.moderation

import app.corkboard.common.ApiException
import app.corkboard.common.ProblemCode
import app.corkboard.jooq.enums.EventStatus as DbEventStatus
import app.corkboard.jooq.enums.ReportReason as DbReportReason
import app.corkboard.jooq.tables.references.EVENTS
import app.corkboard.jooq.tables.references.REPORTS
import app.corkboard.notifications.NotificationKind
import app.corkboard.notifications.NotificationService
import java.util.UUID
import org.jooq.DSLContext
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReportService(
    private val dsl: DSLContext,
    private val notifications: NotificationService,
) {

    @Transactional
    fun report(eventId: UUID, reporterId: UUID, req: ReportRequest) {
        val event = dsl.select(EVENTS.AUTHOR_ID, EVENTS.TITLE, EVENTS.STATUS)
            .from(EVENTS).where(EVENTS.ID.eq(eventId)).fetchOne()
            ?: throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)
        val authorId = event[EVENTS.AUTHOR_ID]!!
        val statusBefore = event[EVENTS.STATUS]
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

        val statusAfter = dsl.select(EVENTS.STATUS).from(EVENTS)
            .where(EVENTS.ID.eq(eventId)).fetchOne(EVENTS.STATUS)
        if (statusAfter == DbEventStatus.under_review && statusBefore != DbEventStatus.under_review) {
            notifications.create(
                authorId,
                NotificationKind.EVENT_UNDER_REVIEW,
                mapOf("eventId" to eventId.toString(), "eventTitle" to event[EVENTS.TITLE]),
            )
        }
    }
}
