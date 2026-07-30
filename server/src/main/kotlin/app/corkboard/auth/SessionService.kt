package app.corkboard.auth

import app.corkboard.common.CorkboardProperties
import app.corkboard.jooq.tables.references.SESSIONS
import app.corkboard.jooq.tables.references.USERS
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID
import org.jooq.DSLContext
import org.springframework.stereotype.Service

data class SessionUser(
    val userId: UUID,
    val sessionId: UUID,
    val email: String,
    val displayName: String,
    val avatarSeed: String,
    val emailVerified: Boolean,
    val createdAt: OffsetDateTime,
) {
    fun toResponse(): UserResponse =
        UserResponse(userId, email, displayName, avatarSeed, emailVerified, createdAt.toInstant())
}

@Service
class SessionService(
    private val dsl: DSLContext,
    private val props: CorkboardProperties,
    private val clock: Clock,
) {

    private val random = SecureRandom()

    fun create(userId: UUID, userAgent: String?): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        dsl.insertInto(SESSIONS)
            .set(SESSIONS.USER_ID, userId)
            .set(SESSIONS.TOKEN_HASH, sha256(token))
            .set(SESSIONS.USER_AGENT, userAgent)
            .set(SESSIONS.EXPIRES_AT, OffsetDateTime.now(clock).plusDays(props.sessionTtlDays))
            .execute()
        return token
    }

    fun resolve(token: String): SessionUser? {
        val now = OffsetDateTime.now(clock)
        val row = dsl.select(
            SESSIONS.ID, SESSIONS.LAST_SEEN_AT,
            USERS.ID, USERS.EMAIL, USERS.DISPLAY_NAME, USERS.AVATAR_SEED, USERS.EMAIL_VERIFIED_AT, USERS.CREATED_AT,
        )
            .from(SESSIONS)
            .join(USERS).on(SESSIONS.USER_ID.eq(USERS.ID))
            .where(SESSIONS.TOKEN_HASH.eq(sha256(token)), SESSIONS.EXPIRES_AT.gt(now))
            .fetchOne() ?: return null

        val sessionId = row[SESSIONS.ID]!!
        if (row[SESSIONS.LAST_SEEN_AT]!!.isBefore(now.minusMinutes(5))) {
            dsl.update(SESSIONS)
                .set(SESSIONS.LAST_SEEN_AT, now)
                .set(SESSIONS.EXPIRES_AT, now.plusDays(props.sessionTtlDays))
                .where(SESSIONS.ID.eq(sessionId))
                .execute()
        }
        return SessionUser(
            userId = row[USERS.ID]!!,
            sessionId = sessionId,
            email = row[USERS.EMAIL]!!,
            displayName = row[USERS.DISPLAY_NAME]!!,
            avatarSeed = row[USERS.AVATAR_SEED]!!,
            emailVerified = row[USERS.EMAIL_VERIFIED_AT] != null,
            createdAt = row[USERS.CREATED_AT]!!,
        )
    }

    fun delete(sessionId: UUID) {
        dsl.deleteFrom(SESSIONS).where(SESSIONS.ID.eq(sessionId)).execute()
    }

    private fun sha256(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.US_ASCII))
            .joinToString("") { "%02x".format(it) }
}
