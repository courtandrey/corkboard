package app.corkboard.jobs

import app.corkboard.ApiTestBase
import app.corkboard.jooq.tables.references.EMAIL_VERIFICATIONS
import app.corkboard.jooq.tables.references.SESSIONS
import java.time.OffsetDateTime
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class HousekeepingTest : ApiTestBase() {

    @Autowired
    lateinit var housekeeping: Housekeeping

    private fun insertSession(userId: UUID, expiresAt: OffsetDateTime): UUID =
        dsl.insertInto(SESSIONS)
            .set(SESSIONS.USER_ID, userId)
            .set(SESSIONS.TOKEN_HASH, "test-${UUID.randomUUID()}")
            .set(SESSIONS.EXPIRES_AT, expiresAt)
            .returning(SESSIONS.ID)
            .fetchOne(SESSIONS.ID)!!

    private fun insertVerification(userId: UUID, expiresAt: OffsetDateTime, consumedAt: OffsetDateTime? = null): UUID =
        dsl.insertInto(EMAIL_VERIFICATIONS)
            .set(EMAIL_VERIFICATIONS.USER_ID, userId)
            .set(EMAIL_VERIFICATIONS.TOKEN_HASH, "test-${UUID.randomUUID()}")
            .set(EMAIL_VERIFICATIONS.EXPIRES_AT, expiresAt)
            .set(EMAIL_VERIFICATIONS.CONSUMED_AT, consumedAt)
            .returning(EMAIL_VERIFICATIONS.ID)
            .fetchOne(EMAIL_VERIFICATIONS.ID)!!

    @Test
    fun `sweeps what is dead and leaves what is live`() {
        val user = registerUser("Tidy Resident")
        val now = OffsetDateTime.now()

        val expiredSession = insertSession(user.id, now.minusDays(1))
        val liveSession = insertSession(user.id, now.plusDays(10))

        val longExpired = insertVerification(user.id, now.minusDays(40))
        val longConsumed = insertVerification(user.id, now.plusDays(1), consumedAt = now.minusDays(40))
        val freshlyExpired = insertVerification(user.id, now.minusHours(1))
        val pending = insertVerification(user.id, now.plusDays(1))

        housekeeping.sweep()

        val sessions = dsl.select(SESSIONS.ID).from(SESSIONS).where(SESSIONS.USER_ID.eq(user.id)).fetch(SESSIONS.ID)
        assertThat(sessions).contains(liveSession).doesNotContain(expiredSession)

        val verifications = dsl.select(EMAIL_VERIFICATIONS.ID).from(EMAIL_VERIFICATIONS)
            .where(EMAIL_VERIFICATIONS.USER_ID.eq(user.id)).fetch(EMAIL_VERIFICATIONS.ID)
        assertThat(verifications)
            .describedAs("a recently expired link still answers 'expired' politely; only long-dead rows go")
            .contains(freshlyExpired, pending)
            .doesNotContain(longExpired, longConsumed)

        assertThat(getJson("/api/v1/auth/me", user.headers).statusCode.value())
            .describedAs("the account's real session is untouched")
            .isEqualTo(200)
    }
}
