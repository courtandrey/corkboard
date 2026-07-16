package app.corkboard.auth

import app.corkboard.common.CorkboardProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.core.type.AnnotatedTypeMetadata
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher

class GoogleAuthEnabled : Condition {
    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean =
        !context.environment.getProperty("corkboard.google-client-id").isNullOrBlank()
}

class GoogleLoginCustomizer(
    private val resolver: OAuth2AuthorizationRequestResolver,
    private val cookieRepository: CookieAuthorizationRequestRepository,
    private val identity: GoogleIdentityService,
    private val sessions: SessionService,
    private val cookies: SessionCookies,
    private val webOrigin: String,
) {

    fun configure(http: HttpSecurity) {
        http.oauth2Login { login ->
            login.authorizationEndpoint {
                it.authorizationRequestResolver(resolver)
                it.authorizationRequestRepository(cookieRepository)
            }
            login.redirectionEndpoint { it.baseUri("/api/v1/auth/google/callback") }
            login.successHandler { request, response, authentication ->
                val oidcUser = authentication.principal as OidcUser
                val result = identity.createOrLink(
                    GoogleProfile(
                        sub = oidcUser.subject,
                        email = oidcUser.email,
                        emailVerified = oidcUser.emailVerified ?: false,
                        name = oidcUser.fullName,
                    )
                )
                SecurityContextHolder.clearContext()
                when (result) {
                    is GoogleIdentityResult.SignedIn -> {
                        val token = sessions.create(result.userId, request.getHeader("User-Agent"))
                        response.addHeader("Set-Cookie", cookies.session(token))
                        response.sendRedirect("$webOrigin/")
                    }
                    is GoogleIdentityResult.EmailConflict ->
                        response.sendRedirect("$webOrigin/?authError=google")
                }
            }
            login.failureHandler { _, response, _ ->
                response.sendRedirect("$webOrigin/?authError=google")
            }
        }
    }
}

@Configuration
@Conditional(GoogleAuthEnabled::class)
class GoogleOAuthConfig {

    @Bean
    fun clientRegistrationRepository(props: CorkboardProperties): ClientRegistrationRepository =
        InMemoryClientRegistrationRepository(
            CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(props.googleClientId)
                .clientSecret(props.googleClientSecret)
                .redirectUri(props.googleCallbackUrl)
                .scope("openid", "email", "profile")
                .build()
        )

    @Bean
    fun googleLoginCustomizer(
        registrations: ClientRegistrationRepository,
        identity: GoogleIdentityService,
        sessions: SessionService,
        cookies: SessionCookies,
        props: CorkboardProperties,
    ): GoogleLoginCustomizer {
        val delegate = DefaultOAuth2AuthorizationRequestResolver(registrations, "/api/v1/auth")
        val googleEntry = PathPatternRequestMatcher.withDefaults()
            .matcher(HttpMethod.GET, "/api/v1/auth/google")
        val resolver = object : OAuth2AuthorizationRequestResolver {
            override fun resolve(request: jakarta.servlet.http.HttpServletRequest) =
                if (googleEntry.matches(request)) delegate.resolve(request) else null

            override fun resolve(request: jakarta.servlet.http.HttpServletRequest, clientRegistrationId: String) =
                delegate.resolve(request, clientRegistrationId)
        }
        return GoogleLoginCustomizer(
            resolver,
            CookieAuthorizationRequestRepository(props.cookieSecure),
            identity,
            sessions,
            cookies,
            props.webOrigin,
        )
    }
}
