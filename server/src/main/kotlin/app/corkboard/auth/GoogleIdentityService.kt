package app.corkboard.auth

import app.corkboard.jooq.tables.references.USERS
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class GoogleProfile(
    val sub: String,
    val email: String,
    val emailVerified: Boolean,
    val name: String?,
)

sealed interface GoogleIdentityResult {
    data class SignedIn(val userId: UUID) : GoogleIdentityResult

    data object NeedsSignup : GoogleIdentityResult

    data object EmailConflict : GoogleIdentityResult
}

@Service
class GoogleIdentityService(private val dsl: DSLContext, private val clock: Clock) {

    @Transactional
    fun signIn(profile: GoogleProfile): GoogleIdentityResult {
        val bySub = dsl.select(USERS.ID).from(USERS)
            .where(USERS.GOOGLE_SUB.eq(profile.sub))
            .fetchOne(USERS.ID)
        if (bySub != null) return GoogleIdentityResult.SignedIn(bySub)

        val byEmail = dsl.select(USERS.ID).from(USERS)
            .where(USERS.EMAIL.eq(profile.email))
            .fetchOne(USERS.ID)
        if (byEmail != null) {
            if (!profile.emailVerified) return GoogleIdentityResult.EmailConflict
            dsl.update(USERS)
                .set(USERS.GOOGLE_SUB, profile.sub)
                .set(USERS.EMAIL_VERIFIED_AT, OffsetDateTime.now(clock))
                .where(USERS.ID.eq(byEmail))
                .execute()
            return GoogleIdentityResult.SignedIn(byEmail)
        }

        return GoogleIdentityResult.NeedsSignup
    }

    @Transactional
    fun create(profile: GoogleProfile, handle: String, displayName: String): UUID =
        dsl.insertInto(USERS)
            .set(USERS.EMAIL, profile.email)
            .set(USERS.HANDLE, handle)
            .set(USERS.DISPLAY_NAME, displayName)
            .set(USERS.GOOGLE_SUB, profile.sub)
            .set(USERS.AVATAR_SEED, UUID.randomUUID().toString())
            .set(USERS.EMAIL_VERIFIED_AT, if (profile.emailVerified) OffsetDateTime.now(clock) else null)
            .returning(USERS.ID)
            .fetchOne(USERS.ID)!!
}
