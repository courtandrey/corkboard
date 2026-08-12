package app.corkboard.auth

import app.corkboard.common.ProblemCode
import org.springframework.security.core.context.SecurityContextHolder

fun denialCode(): ProblemCode {
    val auth = SecurityContextHolder.getContext().authentication as? SessionAuthentication
    return if (auth != null && !auth.user.emailVerified) ProblemCode.EMAIL_UNVERIFIED else ProblemCode.FORBIDDEN
}
