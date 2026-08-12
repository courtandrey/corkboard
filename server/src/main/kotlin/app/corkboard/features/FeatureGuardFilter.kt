package app.corkboard.features

import app.corkboard.common.ProblemCode
import app.corkboard.common.Problems
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.server.PathContainer
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.pattern.PathPattern
import org.springframework.web.util.pattern.PathPatternParser

class FeatureGuardFilter(
    private val flags: FeatureFlagService,
    private val problems: Problems,
) : OncePerRequestFilter() {

    private data class Route(val method: HttpMethod, val pattern: PathPattern, val flag: FeatureFlag)

    private val routes = PathPatternParser().let { parser ->
        FeatureGuards.all.map { Route(it.method, parser.parse(it.pattern), it.flag) }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val method = HttpMethod.valueOf(request.method)
        val path = PathContainer.parsePath(request.requestURI.removePrefix(request.contextPath))
        val closed = routes.any { it.method == method && it.pattern.matches(path) && !flags.isEnabled(it.flag) }

        if (closed) {
            problems.write(response, HttpStatus.FORBIDDEN, ProblemCode.FEATURE_DISABLED)
            return
        }
        chain.doFilter(request, response)
    }
}
