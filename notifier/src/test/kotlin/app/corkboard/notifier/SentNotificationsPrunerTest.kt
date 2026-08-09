package app.corkboard.notifier

import app.corkboard.notifier.jooq.tables.references.SENT_NOTIFICATIONS
import app.corkboard.notifier.notifications.SentNotificationsPruner
import java.time.OffsetDateTime
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class SentNotificationsPrunerTest : NotifierTestBase() {

    @Autowired
    lateinit var pruner: SentNotificationsPruner

    @Autowired
    lateinit var dsl: DSLContext

    private fun claim(id: String, sentAt: OffsetDateTime) {
        dsl.insertInto(SENT_NOTIFICATIONS)
            .set(SENT_NOTIFICATIONS.IDEMPOTENCY_ID, id)
            .set(SENT_NOTIFICATIONS.TYPE, "EMAIL_VERIFICATION")
            .set(SENT_NOTIFICATIONS.SENT_AT, sentAt)
            .execute()
    }

    @Test
    fun `claims older than any possible redelivery go, recent ones stay`() {
        val stale = "prune-old-${System.nanoTime()}"
        val recent = "prune-new-${System.nanoTime()}"
        claim(stale, OffsetDateTime.now().minusDays(40))
        claim(recent, OffsetDateTime.now().minusDays(5))

        pruner.prune()

        val remaining = dsl.select(SENT_NOTIFICATIONS.IDEMPOTENCY_ID).from(SENT_NOTIFICATIONS)
            .where(SENT_NOTIFICATIONS.IDEMPOTENCY_ID.`in`(stale, recent))
            .fetch(SENT_NOTIFICATIONS.IDEMPOTENCY_ID)
        assertThat(remaining)
            .describedAs("a 5-day-old claim can still meet a redelivery; a 40-day-old one cannot")
            .containsExactly(recent)
    }
}
