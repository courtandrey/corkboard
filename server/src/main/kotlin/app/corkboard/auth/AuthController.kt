package app.corkboard.auth

import app.corkboard.common.ApiException
import app.corkboard.common.CorkboardProperties
import app.corkboard.common.ProblemCode
import app.corkboard.common.RateLimiter
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import java.net.URI
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val verifications: EmailVerificationService,
    private val cookies: SessionCookies,
    private val props: CorkboardProperties,
) {

    private val resendLimiter = RateLimiter(RESEND_PER_MINUTE)

    @PostMapping("/register")
    fun register(
        @Valid @RequestBody req: RegisterRequest,
        request: HttpServletRequest,
    ): ResponseEntity<AuthResponse> {
        val result = authService.register(req, request.getHeader("User-Agent"))
        return respond(HttpStatus.CREATED, result, req.transport ?: SessionTransport.COOKIE)
    }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody req: LoginRequest,
        request: HttpServletRequest,
    ): ResponseEntity<AuthResponse> {
        val result = authService.login(req, request.remoteAddr, request.getHeader("User-Agent"))
        return respond(HttpStatus.OK, result, req.transport ?: SessionTransport.COOKIE)
    }

    @PostMapping("/logout")
    fun logout(auth: SessionAuthentication): ResponseEntity<Void> {
        authService.logout(auth.user.sessionId)
        val response = ResponseEntity.noContent()
        if (auth.transport == SessionTransport.COOKIE) {
            response.header(HttpHeaders.SET_COOKIE, cookies.expired())
        }
        return response.build()
    }

    @GetMapping("/me")
    fun me(auth: SessionAuthentication): AuthResponse = AuthResponse(auth.user.toResponse())

    @PatchMapping("/me")
    fun updateMe(
        auth: SessionAuthentication,
        @Valid @RequestBody req: UpdateProfileRequest,
    ): AuthResponse = AuthResponse(authService.updateProfile(auth.user.userId, req))

    @GetMapping("/verify")
    fun verify(@RequestParam token: String): ResponseEntity<Void> {
        val outcome = verifications.verify(token)
        val flag = when (outcome) {
            VerificationOutcome.VERIFIED -> "1"
            VerificationOutcome.ALREADY_VERIFIED -> "already"
            VerificationOutcome.EXPIRED -> "expired"
            VerificationOutcome.INVALID -> "invalid"
        }
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create("${props.webOrigin}/?verified=$flag"))
            .build()
    }

    @PostMapping("/verification/resend")
    fun resend(auth: SessionAuthentication): ResponseEntity<Void> {
        if (auth.user.emailVerified) return ResponseEntity.noContent().build()
        if (!resendLimiter.tryConsume("user:${auth.user.userId}")) {
            throw ApiException(HttpStatus.TOO_MANY_REQUESTS, ProblemCode.RATE_LIMITED)
        }
        verifications.issue(auth.user.userId, auth.user.email, auth.user.displayName)
        return ResponseEntity.accepted().build()
    }

    private fun respond(
        status: HttpStatus,
        result: AuthenticatedUser,
        transport: SessionTransport,
    ): ResponseEntity<AuthResponse> = when (transport) {
        SessionTransport.COOKIE ->
            ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.session(result.token))
                .body(AuthResponse(result.user))
        SessionTransport.BEARER ->
            ResponseEntity.status(status).body(AuthResponse(result.user, result.token))
    }

    private companion object {
        const val RESEND_PER_MINUTE = 2
    }
}
