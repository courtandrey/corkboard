package app.corkboard.jobs

import app.corkboard.jooq.enums.EventStatus as DbEventStatus
import app.corkboard.jooq.tables.references.EVENTS
import app.corkboard.notifications.NotificationKind
import app.corkboard.notifications.NotificationService
import java.time.Clock
import java.time.OffsetDateTime
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ExpirationSweep(
    private val dsl: DSLContext,
    private val notifications: NotificationService,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 3_600_000)
    fun scheduled() {
        sweep()
    }

    @Transactional
    fun sweep() {
        val now = OffsetDateTime.now(clock)

        val expired = dsl.update(EVENTS)
            .set(EVENTS.STATUS, DbEventStatus.expired)
            .where(EVENTS.STATUS.eq(DbEventStatus.active), EVENTS.EXPIRES_AT.le(now))
            .execute()
        if (expired > 0) log.info("Expired {} overdue events", expired)

        val expiring = dsl.select(EVENTS.ID, EVENTS.AUTHOR_ID, EVENTS.TITLE, EVENTS.EXPIRES_AT)
            .from(EVENTS)
            .where(
                EVENTS.STATUS.eq(DbEventStatus.active),
                EVENTS.EXPIRES_AT.gt(now),
                EVENTS.EXPIRES_AT.le(now.plusHours(72)),
            )
            .fetch()
        for (row in expiring) {
            val eventId = row[EVENTS.ID]!!
            val authorId = row[EVENTS.AUTHOR_ID]!!
            if (!notifications.existsForEvent(authorId, NotificationKind.EVENT_EXPIRING, eventId)) {
                notifications.create(
                    authorId,
                    NotificationKind.EVENT_EXPIRING,
                    mapOf(
                        "eventId" to eventId.toString(),
                        "eventTitle" to row[EVENTS.TITLE],
                        "expiresAt" to row[EVENTS.EXPIRES_AT]!!.toInstant().toString(),
                    ),
                )
            }
        }
    }
}
