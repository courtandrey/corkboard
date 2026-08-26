package app.corkboard.auth

import app.corkboard.common.ApiException
import app.corkboard.common.ProblemCode
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GoogleSignupService(
    private val pending: PendingSignups,
    private val identity: GoogleIdentityService,
    private val sessions: SessionService,
    private val auth: AuthService,
) {

    @Transactional
    fun complete(req: CompleteSignupRequest, userAgent: String?): AuthenticatedUser {
        val profile = pending.open(req.token)
            ?: throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.SIGNUP_EXPIRED)
        val name = req.displayName.trim()
        if (name.isEmpty()) {
            throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.VALIDATION_FAILED)
        }
        val handle = auth.handle(req.handle)

        val userId = try {
            identity.create(profile, handle, name)
        } catch (taken: DuplicateKeyException) {
            throw ApiException(HttpStatus.CONFLICT, AuthService.claimedAlready(taken))
        }
        return AuthenticatedUser(auth.userById(userId), sessions.create(userId, userAgent))
    }
}
