package app.corkboard.auth

import app.corkboard.common.ApiException
import app.corkboard.common.CorkboardProperties
import app.corkboard.common.ProblemCode
import app.corkboard.common.RateLimiter
import app.corkboard.jooq.tables.records.UsersRecord
import app.corkboard.jooq.tables.references.USERS
import java.util.UUID
import org.jooq.DSLContext
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

data class AuthenticatedUser(val user: UserResponse, val token: String)

@Service
class AuthService(
    private val dsl: DSLContext,
    private val passwords: PasswordService,
    private val sessions: SessionService,
    private val verifications: EmailVerificationService,
    private val userRoles: UserRoleService,
    private val catalog: RoleCatalog,
    props: CorkboardProperties,
) {

    private val ipLimiter = RateLimiter(props.authRate.perIp)
    private val emailLimiter = RateLimiter(props.authRate.perEmail)

    private val dummyHash = passwords.hash(UUID.randomUUID().toString())

    fun register(req: RegisterRequest, userAgent: String?): AuthenticatedUser {
        if (passwords.isBreached(req.password)) {
            throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.BREACHED_PASSWORD)
        }
        val record = try {
            dsl.insertInto(USERS)
                .set(USERS.EMAIL, req.email.trim())
                .set(USERS.DISPLAY_NAME, req.displayName.trim())
                .set(USERS.PASSWORD_HASH, passwords.hash(req.password))
                .set(USERS.AVATAR_SEED, UUID.randomUUID().toString())
                .returning()
                .fetchOne()!!
        } catch (e: DuplicateKeyException) {
            throw ApiException(HttpStatus.CONFLICT, ProblemCode.EMAIL_TAKEN)
        }
        val user = toResponse(record)
        verifications.issue(user.id, user.email, user.displayName)
        return AuthenticatedUser(user, sessions.create(user.id, userAgent))
    }

    private fun toResponse(record: UsersRecord): UserResponse {
        val emailVerified = record.emailVerifiedAt != null
        val roles = userRoles.rolesOf(record.id!!, emailVerified)
        return UserResponse(
            id = record.id!!,
            email = record.email!!,
            displayName = record.displayName!!,
            avatarSeed = record.avatarSeed!!,
            emailVerified = emailVerified,
            createdAt = record.createdAt!!.toInstant(),
            roles = roles.sorted(),
            permissions = catalog.permissionsOf(roles).map { it.name }.sorted(),
        )
    }

    fun updateProfile(userId: UUID, req: UpdateProfileRequest): UserResponse {
        val name = req.displayName.trim()
        if (name.isEmpty()) {
            throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.VALIDATION_FAILED)
        }
        val record = dsl.update(USERS)
            .set(USERS.DISPLAY_NAME, name)
            .where(USERS.ID.eq(userId))
            .returning()
            .fetchOne()
            ?: throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)
        return toResponse(record)
    }

    fun login(req: LoginRequest, clientIp: String, userAgent: String?): AuthenticatedUser {
        val ipAllowed = ipLimiter.tryConsume("ip:$clientIp")
        val emailAllowed = emailLimiter.tryConsume("email:${req.email.trim().lowercase()}")
        if (!ipAllowed || !emailAllowed) {
            throw ApiException(HttpStatus.TOO_MANY_REQUESTS, ProblemCode.RATE_LIMITED)
        }

        val record = dsl.selectFrom(USERS).where(USERS.EMAIL.eq(req.email.trim())).fetchOne()
        val matches = passwords.verify(req.password, record?.passwordHash ?: dummyHash)
        if (record?.passwordHash == null || !matches) {
            throw ApiException(HttpStatus.UNAUTHORIZED, ProblemCode.INVALID_CREDENTIALS)
        }

        val user = toResponse(record)
        return AuthenticatedUser(user, sessions.create(user.id, userAgent))
    }

    fun logout(sessionId: UUID) {
        sessions.delete(sessionId)
    }
}
