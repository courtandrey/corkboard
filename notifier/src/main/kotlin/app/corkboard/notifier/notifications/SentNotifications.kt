package app.corkboard.notifier.notifications

import app.corkboard.notifier.jooq.tables.references.SENT_NOTIFICATIONS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class SentNotifications(private val dsl: DSLContext) {

    fun claim(idempotencyId: String, type: String): Boolean =
        dsl.insertInto(SENT_NOTIFICATIONS)
            .set(SENT_NOTIFICATIONS.IDEMPOTENCY_ID, idempotencyId)
            .set(SENT_NOTIFICATIONS.TYPE, type)
            .onConflictDoNothing()
            .execute() == 1
}
