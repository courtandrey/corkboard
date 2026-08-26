package app.corkboard.auth

import app.corkboard.ApiTestBase
import java.net.HttpURLConnection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpHeaders
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.test.context.TestPropertySource
import org.springframework.web.client.RestTemplate

@TestPropertySource(
    properties = [
        "corkboard.google-client-id=test-client-id",
        "corkboard.google-client-secret=test-client-secret",
        "corkboard.google-callback-url=http://localhost:8081/api/v1/auth/google/callback",
    ]
)
class GoogleRedirectTest : ApiTestBase() {

    @LocalServerPort
    var port: Int = 0

    private val noRedirects = RestTemplate(object : SimpleClientHttpRequestFactory() {
        override fun prepareConnection(connection: HttpURLConnection, httpMethod: String) {
            super.prepareConnection(connection, httpMethod)
            connection.instanceFollowRedirects = false
        }
    })

    @Test
    fun `google entry point redirects to Google with state, nonce and a signed flow cookie`() {
        val res = noRedirects.getForEntity("http://localhost:$port/api/v1/auth/google", String::class.java)

        assertThat(res.statusCode.value()).isEqualTo(302)
        val location = res.headers.location!!.toString()
        assertThat(location).startsWith("https://accounts.google.com/o/oauth2/v2/auth")
        assertThat(location).contains("client_id=test-client-id").contains("state=").contains("nonce=")

        val flowCookie = res.headers[HttpHeaders.SET_COOKIE]!!.first { it.startsWith("cb_oauth=") }
        assertThat(flowCookie).contains("HttpOnly").contains("Max-Age=300")
    }

    @Test
    fun `meta reports googleAuth true when configured`() {
        val res = rest.getForEntity("/api/v1/meta", Map::class.java)
        assertThat(res.body?.get("googleAuth")).isEqualTo(true)
    }

    @Test
    fun `password login still works with the google matcher installed`() {
        val email = "coexist-${java.util.UUID.randomUUID()}@example.com"
        val password = "Pw-${java.util.UUID.randomUUID()}"
        val register = rest.postForEntity(
            "/api/v1/auth/register",
            mapOf(
                "email" to email,
                "password" to password,
                "displayName" to "Coexists",
                "handle" to "coexists_${java.util.UUID.randomUUID().toString().take(8)}".replace("-", ""),
            ),
            String::class.java,
        )
        assertThat(register.statusCode.value()).isEqualTo(201)

        val login = rest.postForEntity(
            "/api/v1/auth/login",
            mapOf("email" to email, "password" to password),
            String::class.java,
        )
        assertThat(login.statusCode.value()).isEqualTo(200)
    }
}
