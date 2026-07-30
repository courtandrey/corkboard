package app.corkboard.auth

import app.corkboard.common.CorkboardProperties
import app.corkboard.jooq.tables.references.EMAIL_VERIFICATIONS
import app.corkboard.jooq.tables.references.USERS
import app.corkboard.notifications.NotificationPublisher
import app.corkboard.notifications.avro.NotificationType
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

enum class VerificationOutcome { VERIFIED, ALREADY_VERIFIED, EXPIRED, INVALID }

@Service
class EmailVerificationService(
    private val dsl: DSLContext,
    private val notifications: NotificationPublisher,
    private val props: CorkboardProperties,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    @Transactional
    fun issue(userId: UUID, email: String, displayName: String): String {
        val token = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(32).also(random::nextBytes))

        val id = dsl.insertInto(EMAIL_VERIFICATIONS)
            .set(EMAIL_VERIFICATIONS.USER_ID, userId)
            .set(EMAIL_VERIFICATIONS.TOKEN_HASH, sha256(token))
            .set(EMAIL_VERIFICATIONS.EXPIRES_AT, OffsetDateTime.now(clock).plusHours(TTL_HOURS))
            .returning(EMAIL_VERIFICATIONS.ID)
            .fetchOne(EMAIL_VERIFICATIONS.ID)!!

        notifications.publish(
            idempotencyId = id.toString(),
            type = NotificationType.EMAIL_VERIFICATION,
            email = email,
            name = displayName,
            variables = mapOf(
                "user_name" to displayName,
                "verification_link" to link(token),
            ),
        )
        return token
    }

    @Transactional
    fun verify(token: String): VerificationOutcome {
        val now = OffsetDateTime.now(clock)
        val row = dsl.select(EMAIL_VERIFICATIONS.ID, EMAIL_VERIFICATIONS.USER_ID, EMAIL_VERIFICATIONS.EXPIRES_AT, EMAIL_VERIFICATIONS.CONSUMED_AT, USERS.EMAIL_VERIFIED_AT)
            .from(EMAIL_VERIFICATIONS)
            .join(USERS).on(USERS.ID.eq(EMAIL_VERIFICATIONS.USER_ID))
            .where(EMAIL_VERIFICATIONS.TOKEN_HASH.eq(sha256(token)))
            .fetchOne()
            ?: return VerificationOutcome.INVALID

        if (row[USERS.EMAIL_VERIFIED_AT] != null) return VerificationOutcome.ALREADY_VERIFIED
        if (row[EMAIL_VERIFICATIONS.CONSUMED_AT] != null) return VerificationOutcome.INVALID
        if (row[EMAIL_VERIFICATIONS.EXPIRES_AT]!!.isBefore(now)) return VerificationOutcome.EXPIRED

        dsl.update(EMAIL_VERIFICATIONS)
            .set(EMAIL_VERIFICATIONS.CONSUMED_AT, now)
            .where(EMAIL_VERIFICATIONS.ID.eq(row[EMAIL_VERIFICATIONS.ID]))
            .execute()
        dsl.update(USERS)
            .set(USERS.EMAIL_VERIFIED_AT, now)
            .where(USERS.ID.eq(row[EMAIL_VERIFICATIONS.USER_ID]))
            .execute()

        log.info("verified email for user {}", row[EMAIL_VERIFICATIONS.USER_ID])
        return VerificationOutcome.VERIFIED
    }

    private fun link(token: String): String =
        "${props.webOrigin}/api/v1/auth/verify?token=${URLEncoder.encode(token, StandardCharsets.UTF_8)}"

    private fun sha256(value: String): String =
        Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        )

    private companion object {
        const val TTL_HOURS = 48L
    }
}
