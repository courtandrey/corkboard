package app.corkboard.auth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest


class CookieAuthorizationRequestRepository(
    private val cookieSecure: Boolean,
) : AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    companion object {
        const val COOKIE_NAME = "cb_oauth"
        private const val COOKIE_PATH = "/api/v1/auth"
        private val MAX_AGE: Duration = Duration.ofMinutes(5)
    }

    private val hmacKey = SecretKeySpec(ByteArray(32).also(SecureRandom()::nextBytes), "HmacSHA256")

    override fun loadAuthorizationRequest(request: HttpServletRequest): OAuth2AuthorizationRequest? {
        val cookie = request.cookies?.firstOrNull { it.name == COOKIE_NAME } ?: return null
        val (payload, signature) = cookie.value.split('.').takeIf { it.size == 2 } ?: return null
        if (!MessageDigest.isEqual(sign(payload).toByteArray(), signature.toByteArray())) return null
        return runCatching {
            ObjectInputStream(ByteArrayInputStream(Base64.getUrlDecoder().decode(payload)))
                .use { it.readObject() as OAuth2AuthorizationRequest }
        }.getOrNull()
    }

    override fun saveAuthorizationRequest(
        authorizationRequest: OAuth2AuthorizationRequest?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        if (authorizationRequest == null) {
            expire(response)
            return
        }
        val bytes = ByteArrayOutputStream().also { buf ->
            ObjectOutputStream(buf).use { it.writeObject(authorizationRequest) }
        }.toByteArray()
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        setCookie(response, "$payload.${sign(payload)}", MAX_AGE)
    }

    override fun removeAuthorizationRequest(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): OAuth2AuthorizationRequest? {
        val loaded = loadAuthorizationRequest(request)
        expire(response)
        return loaded
    }

    private fun sign(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256").also { it.init(hmacKey) }
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal(payload.toByteArray(Charsets.US_ASCII)))
    }

    private fun expire(response: HttpServletResponse) = setCookie(response, "", Duration.ZERO)

    private fun setCookie(response: HttpServletResponse, value: String, maxAge: Duration) {
        response.addHeader(
            HttpHeaders.SET_COOKIE,
            ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build()
                .toString(),
        )
    }
}
