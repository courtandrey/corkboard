package app.corkboard.auth

import app.corkboard.ApiTestBase
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["corkboard.auth-rate.per-ip=10000"])
class AuthApiTest : ApiTestBase() {

    private fun uniqueEmail() = "user-${UUID.randomUUID()}@example.com"

    private fun strongPassword() = "Pw-${UUID.randomUUID()}"

    private fun post(path: String, body: Map<String, Any?>?, headers: HttpHeaders = HttpHeaders()): ResponseEntity<String> {
        headers.contentType = MediaType.APPLICATION_JSON
        val json = body?.let { mapper.writeValueAsString(it) }
        return rest.exchange(path, HttpMethod.POST, HttpEntity(json, headers), String::class.java)
    }

    private fun get(path: String, headers: HttpHeaders = HttpHeaders()): ResponseEntity<String> =
        rest.exchange(path, HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)

    private fun sessionCookie(res: ResponseEntity<String>): String? =
        res.headers[HttpHeaders.SET_COOKIE]?.firstOrNull { it.startsWith("cb_session=") }

    private fun cookieHeader(setCookie: String): HttpHeaders =
        HttpHeaders().apply { add(HttpHeaders.COOKIE, setCookie.substringBefore(";")) }

    private fun bearerHeader(token: String): HttpHeaders =
        HttpHeaders().apply { setBearerAuth(token) }

    private fun register(
        email: String = uniqueEmail(),
        password: String = strongPassword(),
        displayName: String = "Test Resident",
        transport: String? = null,
    ): ResponseEntity<String> {
        val body = mutableMapOf<String, Any?>("email" to email, "password" to password, "displayName" to displayName)
        if (transport != null) body["transport"] = transport
        return post("/api/v1/auth/register", body)
    }

    @Test
    fun `cookie transport - register me logout lifecycle`() {
        val email = uniqueEmail()
        val res = register(email = email)
        assertThat(res.statusCode.value()).isEqualTo(201)

        val cookie = sessionCookie(res)!!
        assertThat(cookie).contains("HttpOnly").contains("SameSite=Lax").contains("Path=/")
        assertThat(json(res)["user"]["email"].asText()).isEqualTo(email)
        assertThat(json(res)["user"]["displayName"].asText()).isEqualTo("Test Resident")
        assertThat(json(res)["user"]["avatarSeed"].asText()).isNotBlank
        assertThat(json(res).has("token")).isFalse

        val me = get("/api/v1/auth/me", cookieHeader(cookie))
        assertThat(me.statusCode.value()).isEqualTo(200)
        assertThat(json(me)["user"]["email"].asText()).isEqualTo(email)

        val logout = post("/api/v1/auth/logout", null, cookieHeader(cookie))
        assertThat(logout.statusCode.value()).isEqualTo(204)
        assertThat(sessionCookie(logout)).contains("Max-Age=0")

        val meAfter = get("/api/v1/auth/me", cookieHeader(cookie))
        assertThat(meAfter.statusCode.value()).isEqualTo(401)
        assertThat(json(meAfter)["code"].asText()).isEqualTo("unauthenticated")
    }

    @Test
    fun `bearer transport - token in body no cookie and logout revokes`() {
        val res = register(transport = "bearer")
        assertThat(res.statusCode.value()).isEqualTo(201)
        assertThat(sessionCookie(res)).isNull()

        val token = json(res)["token"].asText()
        assertThat(token).isNotBlank

        val me = get("/api/v1/auth/me", bearerHeader(token))
        assertThat(me.statusCode.value()).isEqualTo(200)

        val logout = post("/api/v1/auth/logout", null, bearerHeader(token))
        assertThat(logout.statusCode.value()).isEqualTo(204)

        assertThat(get("/api/v1/auth/me", bearerHeader(token)).statusCode.value()).isEqualTo(401)
    }

    @Test
    fun `login returns a fresh session and uniform 401 for wrong password and unknown email`() {
        val email = uniqueEmail()
        val password = strongPassword()
        register(email = email, password = password)

        val ok = post("/api/v1/auth/login", mapOf("email" to email, "password" to password))
        assertThat(ok.statusCode.value()).isEqualTo(200)
        assertThat(sessionCookie(ok)).isNotNull

        val wrongPassword = post("/api/v1/auth/login", mapOf("email" to email, "password" to strongPassword()))
        val unknownEmail = post("/api/v1/auth/login", mapOf("email" to uniqueEmail(), "password" to password))
        assertThat(wrongPassword.statusCode.value()).isEqualTo(401)
        assertThat(unknownEmail.statusCode.value()).isEqualTo(401)
        assertThat(wrongPassword.body).isEqualTo(unknownEmail.body)
        assertThat(json(wrongPassword)["code"].asText()).isEqualTo("invalid_credentials")
    }

    @Test
    fun `register rejects a duplicate email case-insensitively`() {
        val email = uniqueEmail()
        register(email = email)
        val dup = register(email = email.uppercase())
        assertThat(dup.statusCode.value()).isEqualTo(409)
        assertThat(json(dup)["code"].asText()).isEqualTo("email_taken")
    }

    @Test
    fun `register validates the password policy`() {
        val tooShort = register(password = "short")
        assertThat(tooShort.statusCode.value()).isEqualTo(422)
        assertThat(json(tooShort)["code"].asText()).isEqualTo("validation_failed")
        assertThat(json(tooShort)["fields"].has("password")).isTrue

        val breached = register(password = "password123")
        assertThat(breached.statusCode.value()).isEqualTo(422)
        assertThat(json(breached)["code"].asText()).isEqualTo("breached_password")
    }

    @Test
    fun `login is rate limited per email after five attempts`() {
        val email = uniqueEmail()
        repeat(5) {
            val res = post("/api/v1/auth/login", mapOf("email" to email, "password" to strongPassword()))
            assertThat(res.statusCode.value()).isEqualTo(401)
        }
        val sixth = post("/api/v1/auth/login", mapOf("email" to email, "password" to strongPassword()))
        assertThat(sixth.statusCode.value()).isEqualTo(429)
        assertThat(json(sixth)["code"].asText()).isEqualTo("rate_limited")
    }

    @Test
    fun `cross-site cookie mutations are rejected`() {
        val cookie = sessionCookie(register())!!

        val corsRejected = cookieHeader(cookie).apply { origin = "https://evil.example" }
        assertThat(post("/api/v1/auth/logout", null, corsRejected).statusCode.value()).isEqualTo(403)

        val crossSite = cookieHeader(cookie).apply { add("Sec-Fetch-Site", "cross-site") }
        val rejected = post("/api/v1/auth/logout", null, crossSite)
        assertThat(rejected.statusCode.value()).isEqualTo(403)
        assertThat(json(rejected)["code"].asText()).isEqualTo("origin_rejected")

        assertThat(get("/api/v1/auth/me", cookieHeader(cookie)).statusCode.value()).isEqualTo(200)

        val sameSite = cookieHeader(cookie).apply { origin = "http://localhost:5173" }
        assertThat(post("/api/v1/auth/logout", null, sameSite).statusCode.value()).isEqualTo(204)
    }

    @Test
    fun `a resident can rename themselves, and it sticks everywhere they appear`() {
        val token = json(register(displayName = "Original Name", transport = "bearer"))["token"].asText()
        val headers = bearerHeader(token)

        val renamed = rest.exchange(
            "/api/v1/auth/me", HttpMethod.PATCH,
            HttpEntity(
                mapper.writeValueAsString(mapOf("displayName" to "  Renamed Resident  ")),
                headers.apply { contentType = MediaType.APPLICATION_JSON },
            ),
            String::class.java,
        )
        assertThat(renamed.statusCode.value()).isEqualTo(200)
        assertThat(json(renamed)["user"]["displayName"].asText())
            .describedAs("trimmed, like registration does")
            .isEqualTo("Renamed Resident")

        val me = json(get("/api/v1/auth/me", bearerHeader(token)))["user"]
        assertThat(me["displayName"].asText()).isEqualTo("Renamed Resident")
        assertThat(me["email"].asText()).describedAs("the address is unchanged and still shown").isNotBlank()

        val blank = rest.exchange(
            "/api/v1/auth/me", HttpMethod.PATCH,
            HttpEntity(mapper.writeValueAsString(mapOf("displayName" to "   ")), bearerHeader(token).apply {
                contentType = MediaType.APPLICATION_JSON
            }),
            String::class.java,
        )
        assertThat(blank.statusCode.value()).describedAs("a blank name is refused").isEqualTo(422)

        assertThat(rest.exchange(
            "/api/v1/auth/me", HttpMethod.PATCH,
            HttpEntity(mapper.writeValueAsString(mapOf("displayName" to "Nobody")), HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }),
            String::class.java,
        ).statusCode.value()).describedAs("and a stranger cannot rename anyone").isEqualTo(401)
    }

    @Test
    fun `bearer mutations are exempt from the cross-site check`() {
        val token = json(register(transport = "bearer"))["token"].asText()
        val headers = bearerHeader(token).apply { add("Sec-Fetch-Site", "cross-site") }
        assertThat(post("/api/v1/auth/logout", null, headers).statusCode.value()).isEqualTo(204)
    }
}
