package app.corkboard.moderation

import app.corkboard.common.ApiException
import app.corkboard.common.ProblemCode
import app.corkboard.events.EventStatus
import app.corkboard.jooq.enums.EventStatus as DbEventStatus
import app.corkboard.jooq.tables.references.EVENTS
import app.corkboard.jooq.tables.references.REPORTS
import app.corkboard.jooq.tables.references.USERS
import app.corkboard.notifications.NotificationKind
import app.corkboard.notifications.NotificationService
import java.time.Instant
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class ReportedReason(val reason: String, val count: Int)

data class ReportedEvent(
    val id: UUID,
    val title: String,
    val status: EventStatus,
    val authorDisplayName: String,
    val reportCount: Int,
    val reasons: List<ReportedReason>,
    val lastReportedAt: Instant,
    val createdAt: Instant,
)

data class ReportQueueResponse(val items: List<ReportedEvent>)

@Service
class AdminService(
    private val dsl: DSLContext,
    private val notifications: NotificationService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun reportQueue(limit: Int): ReportQueueResponse {
        val count = DSL.count(REPORTS.ID)
        val lastAt = DSL.max(REPORTS.CREATED_AT)

        val rows = dsl.select(
            EVENTS.ID, EVENTS.TITLE, EVENTS.STATUS, EVENTS.CREATED_AT,
            USERS.DISPLAY_NAME, count, lastAt,
        )
            .from(REPORTS)
            .join(EVENTS).on(EVENTS.ID.eq(REPORTS.EVENT_ID))
            .join(USERS).on(USERS.ID.eq(EVENTS.AUTHOR_ID))
            .where(REPORTS.REVIEWED_AT.isNull)
            .and(EVENTS.STATUS.ne(DbEventStatus.taken_down))
            .groupBy(EVENTS.ID, EVENTS.TITLE, EVENTS.STATUS, EVENTS.CREATED_AT, USERS.DISPLAY_NAME)
            .orderBy(count.desc(), lastAt.desc())
            .limit(limit)
            .fetch()

        if (rows.isEmpty()) return ReportQueueResponse(emptyList())

        val ids = rows.map { it[EVENTS.ID]!! }
        val breakdown = dsl.select(REPORTS.EVENT_ID, REPORTS.REASON, DSL.count())
            .from(REPORTS)
            .where(REPORTS.EVENT_ID.`in`(ids), REPORTS.REVIEWED_AT.isNull)
            .groupBy(REPORTS.EVENT_ID, REPORTS.REASON)
            .fetchGroups(REPORTS.EVENT_ID)

        return ReportQueueResponse(
            rows.map { r ->
                val eventId = r[EVENTS.ID]!!
                ReportedEvent(
                    id = eventId,
                    title = r[EVENTS.TITLE]!!,
                    status = EventStatus.fromDb(r[EVENTS.STATUS]!!.literal),
                    authorDisplayName = r[USERS.DISPLAY_NAME]!!,
                    reportCount = r[count],
                    reasons = breakdown[eventId].orEmpty()
                        .map { ReportedReason(it[REPORTS.REASON]!!.literal, it.get(2, Int::class.java)) }
                        .sortedByDescending { it.count },
                    lastReportedAt = r[lastAt]!!.toInstant(),
                    createdAt = r[EVENTS.CREATED_AT]!!.toInstant(),
                )
            },
        )
    }

    @Transactional
    fun takeDown(eventId: UUID, moderatorId: UUID) {
        val event = setStatus(eventId, DbEventStatus.taken_down, moderatorId, "took down")
        review(eventId, moderatorId)
        notifications.create(
            event.authorId,
            NotificationKind.EVENT_TAKEN_DOWN,
            mapOf("eventId" to eventId.toString(), "eventTitle" to event.title),
        )
    }

    @Transactional
    fun restore(eventId: UUID, moderatorId: UUID) {
        setStatus(eventId, DbEventStatus.active, moderatorId, "restored")
    }

    @Transactional
    fun approve(eventId: UUID, moderatorId: UUID) {
        val status = dsl.select(EVENTS.STATUS).from(EVENTS).where(EVENTS.ID.eq(eventId)).fetchOne(EVENTS.STATUS)
            ?: throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)
        if (status == DbEventStatus.under_review) {
            setStatus(eventId, DbEventStatus.active, moderatorId, "cleared")
        }
        review(eventId, moderatorId)
        log.info("moderator {} approved event {}", moderatorId, eventId)
    }

    private fun review(eventId: UUID, moderatorId: UUID) {
        dsl.update(REPORTS)
            .set(REPORTS.REVIEWED_AT, DSL.currentOffsetDateTime())
            .set(REPORTS.REVIEWED_BY, moderatorId)
            .where(REPORTS.EVENT_ID.eq(eventId), REPORTS.REVIEWED_AT.isNull)
            .execute()
    }

    private data class Touched(val authorId: UUID, val title: String)

    private fun setStatus(eventId: UUID, status: DbEventStatus, moderatorId: UUID, verb: String): Touched {
        val row = dsl.update(EVENTS)
            .set(EVENTS.STATUS, status)
            .where(EVENTS.ID.eq(eventId))
            .returningResult(EVENTS.AUTHOR_ID, EVENTS.TITLE)
            .fetchOne()
            ?: throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)
        log.info("moderator {} {} event {}", moderatorId, verb, eventId)
        return Touched(row[EVENTS.AUTHOR_ID]!!, row[EVENTS.TITLE]!!)
    }
}
