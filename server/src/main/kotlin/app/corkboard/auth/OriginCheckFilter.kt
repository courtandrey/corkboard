package app.corkboard.auth

import app.corkboard.common.ProblemCode
import app.corkboard.common.Problems
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class OriginCheckFilter(
    private val webOrigin: String,
    private val problems: Problems,
) : OncePerRequestFilter() {

    private val safeMethods = setOf("GET", "HEAD", "OPTIONS")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val auth = SecurityContextHolder.getContext().authentication as? SessionAuthentication
        val cookieMutation = auth?.transport == SessionTransport.COOKIE && request.method !in safeMethods
        if (cookieMutation && !allowed(request)) {
            problems.write(response, HttpStatus.FORBIDDEN, ProblemCode.ORIGIN_REJECTED)
            return
        }
        filterChain.doFilter(request, response)
    }

    private fun allowed(request: HttpServletRequest): Boolean {
        val origin = request.getHeader("Origin")
        if (origin != null) return origin == webOrigin || origin == requestOrigin(request)
        val fetchSite = request.getHeader("Sec-Fetch-Site")
        return fetchSite == null || fetchSite != "cross-site"
    }

    private fun requestOrigin(request: HttpServletRequest): String {
        val defaultPort = if (request.scheme == "https") 443 else 80
        val port = if (request.serverPort == defaultPort) "" else ":${request.serverPort}"
        return "${request.scheme}://${request.serverName}$port"
    }
}
