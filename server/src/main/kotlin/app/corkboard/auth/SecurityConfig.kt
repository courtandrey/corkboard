package app.corkboard.auth

import app.corkboard.common.CorkboardProperties
import app.corkboard.common.ProblemCode
import app.corkboard.common.Problems
import app.corkboard.features.FeatureFlagService
import app.corkboard.features.FeatureGuardFilter
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.AuthorizationFilter
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

private val PUBLIC_READS = arrayOf(
    "/api/v1/health",
    "/api/v1/meta",
    "/api/v1/features",
    "/api/v1/openapi.json",
    "/api/v1/openapi.json/**",
    "/swagger-ui/**",
    "/api/v1/auth/google",
    "/api/v1/auth/google/callback",
    "/api/v1/auth/verify",
    "/api/v1/events",
    "/api/v1/events/*",
    "/api/v1/tags",
    "/api/v1/users/*",
    "/api/v1/places",
    "/ws",
    "/",
    "/index.html",
    "/assets/**",
    "/fonts/**",
    "/favicon.svg",
    "/events/*",
    "/new",
    "/login",
    "/finish-signup",
    "/boards/**",
    "/subscriptions",
    "/subscriptions/**",
    "/me/**",
    "/admin/**",
    "/messages",
    "/messages/*",
)

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val props: CorkboardProperties,
    private val problems: Problems,
) {

    @Bean
    fun filterChain(
        http: HttpSecurity,
        sessions: SessionService,
        flags: FeatureFlagService,
        googleLogin: ObjectProvider<GoogleLoginCustomizer>,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .requestCache { it.disable() }
            .addFilterBefore(SessionAuthFilter(sessions), UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(OriginCheckFilter(props.webOrigin, problems), AuthorizationFilter::class.java)
            .addFilterAfter(FeatureGuardFilter(flags, problems), AuthorizationFilter::class.java)
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.GET, *PUBLIC_READS).permitAll()
                    .requestMatchers(HttpMethod.HEAD, *PUBLIC_READS).permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/google/complete",
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ ->
                    problems.write(response, HttpStatus.UNAUTHORIZED, ProblemCode.UNAUTHENTICATED)
                }
                it.accessDeniedHandler { _, response, _ ->
                    problems.write(response, HttpStatus.FORBIDDEN, denialCode())
                }
            }
        googleLogin.ifAvailable { it.configure(http) }
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource =
        UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration(
                "/api/**",
                CorsConfiguration().apply {
                    allowedOrigins = listOf(props.webOrigin)
                    allowedMethods = listOf("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
                    allowedHeaders = listOf("*")
                    allowCredentials = true
                },
            )
        }
}
