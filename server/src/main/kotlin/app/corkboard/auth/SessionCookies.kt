package app.corkboard.auth

import app.corkboard.common.CorkboardProperties
import java.time.Duration
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component

@Component
class SessionCookies(private val props: CorkboardProperties) {

    companion object {
        const val SESSION_COOKIE = "cb_session"
    }

    fun session(token: String): String = build(token, Duration.ofDays(props.sessionTtlDays))

    fun expired(): String = build("", Duration.ZERO)

    private fun build(value: String, maxAge: Duration): String =
        ResponseCookie.from(SESSION_COOKIE, value)
            .httpOnly(true)
            .secure(props.cookieSecure)
            .sameSite("Lax")
            .path("/")
            .maxAge(maxAge)
            .build()
            .toString()
}
