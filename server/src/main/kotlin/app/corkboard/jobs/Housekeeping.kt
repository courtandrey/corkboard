package app.corkboard.jobs

import app.corkboard.jooq.tables.references.EMAIL_VERIFICATIONS
import app.corkboard.jooq.tables.references.SESSIONS
import java.time.Clock
import java.time.OffsetDateTime
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class Housekeeping(
    private val dsl: DSLContext,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 40 4 * * *")
    fun scheduled() {
        sweep()
    }

    @Transactional
    fun sweep() {
        val now = OffsetDateTime.now(clock)

        val sessions = dsl.deleteFrom(SESSIONS)
            .where(SESSIONS.EXPIRES_AT.le(now))
            .execute()

        val cutoff = now.minusDays(VERIFICATION_GRACE_DAYS)
        val verifications = dsl.deleteFrom(EMAIL_VERIFICATIONS)
            .where(
                EMAIL_VERIFICATIONS.CONSUMED_AT.le(cutoff)
                    .or(EMAIL_VERIFICATIONS.EXPIRES_AT.le(cutoff)),
            )
            .execute()

        if (sessions > 0 || verifications > 0) {
            log.info("housekeeping removed {} expired sessions, {} dead verification tokens", sessions, verifications)
        }
    }

    private companion object {
        const val VERIFICATION_GRACE_DAYS = 30L
    }
}
