package app.corkboard.auth

import app.corkboard.common.ProblemCode
import app.corkboard.common.Problems
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class EmailVerifiedFilter(private val problems: Problems) : OncePerRequestFilter() {

    private val safeMethods = setOf("GET", "HEAD", "OPTIONS")

    private val personalView = Regex("^/api/v1/events/[^/]+/hide$")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val auth = SecurityContextHolder.getContext().authentication as? SessionAuthentication
        val restricted = auth != null &&
            !auth.user.emailVerified &&
            request.method !in safeMethods &&
            !request.requestURI.startsWith("/api/v1/auth/") &&
            !personalView.matches(request.requestURI)

        if (restricted) {
            problems.write(response, HttpStatus.FORBIDDEN, ProblemCode.EMAIL_UNVERIFIED)
            return
        }
        filterChain.doFilter(request, response)
    }
}
