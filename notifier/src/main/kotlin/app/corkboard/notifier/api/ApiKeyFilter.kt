package app.corkboard.notifier.api

import app.corkboard.notifier.config.NotifierProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.security.MessageDigest
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.filter.OncePerRequestFilter

const val API_KEY_HEADER = "X-Api-Key"

class ApiKeyFilter(
    private val props: NotifierProperties,
    private val problems: Problems,
) : OncePerRequestFilter() {

    private val open = setOf("/api/v1/health")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (request.requestURI in open || matches(request.getHeader(API_KEY_HEADER))) {
            filterChain.doFilter(request, response)
            return
        }
        problems.write(response, HttpStatus.UNAUTHORIZED, ProblemCode.UNAUTHENTICATED)
    }

    private fun matches(presented: String?): Boolean {
        if (presented.isNullOrEmpty()) return false
        return MessageDigest.isEqual(
            presented.toByteArray(Charsets.UTF_8),
            props.apiKey.toByteArray(Charsets.UTF_8),
        )
    }
}

@Configuration
class ApiKeyConfig {

    @Bean
    fun apiKeyFilter(props: NotifierProperties, problems: Problems): FilterRegistrationBean<ApiKeyFilter> =
        FilterRegistrationBean(ApiKeyFilter(props, problems)).apply {
            addUrlPatterns("/api/*")
            order = 1
        }
}
