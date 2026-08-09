package app.corkboard.notifier.notifications

import app.corkboard.notifier.jooq.tables.references.SENT_NOTIFICATIONS
import java.time.OffsetDateTime
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class SentNotificationsPruner(private val dsl: DSLContext) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 20 4 * * *")
    fun prune() {
        val removed = dsl.deleteFrom(SENT_NOTIFICATIONS)
            .where(SENT_NOTIFICATIONS.SENT_AT.le(OffsetDateTime.now().minusDays(RETENTION_DAYS)))
            .execute()
        if (removed > 0) log.info("pruned {} sent-notification claims older than {} days", removed, RETENTION_DAYS)
    }

    private companion object {
        const val RETENTION_DAYS = 30L
    }
}
