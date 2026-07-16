package app.corkboard.auth

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val cookies: SessionCookies,
) {

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
}
