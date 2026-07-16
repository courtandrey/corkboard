package app.corkboard.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class SessionAuthFilter(private val sessions: SessionService) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val carrier = extract(request)
        if (carrier != null) {
            val user = sessions.resolve(carrier.first)
            if (user != null) {
                SecurityContextHolder.getContext().authentication = SessionAuthentication(user, carrier.second)
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun extract(request: HttpServletRequest): Pair<String, SessionTransport>? {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ", ignoreCase = true)) {
            val token = header.substring(7).trim()
            if (token.isNotEmpty()) return token to SessionTransport.BEARER
        }
        val cookie = request.cookies?.firstOrNull { it.name == SessionCookies.SESSION_COOKIE }
        if (cookie != null && cookie.value.isNotEmpty()) return cookie.value to SessionTransport.COOKIE
        return null
    }
}
