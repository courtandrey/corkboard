package app.corkboard.auth

import app.corkboard.ApiTestBase
import app.corkboard.jooq.tables.references.EMAIL_VERIFICATIONS
import app.corkboard.jooq.tables.references.USERS
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpMethod

@Timeout(120)
class Spec11EmailVerificationTest : ApiTestBase() {

    @Autowired
    lateinit var verifications: EmailVerificationService

    @LocalServerPort
    var port: Int = 0

    private val browser: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    private fun openLink(token: String): HttpResponse<Void> =
        browser.send(
            HttpRequest.newBuilder(URI.create("http://localhost:$port/api/v1/auth/verify?token=$token"))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.discarding(),
        )

    private fun location(response: HttpResponse<Void>): String =
        response.headers().firstValue("Location").orElseThrow()

    private fun issuedLinks(email: String): Int =
        dsl.fetchCount(
            EMAIL_VERIFICATIONS,
            EMAIL_VERIFICATIONS.USER_ID.eq(
                dsl.select(USERS.ID).from(USERS).where(USERS.EMAIL.eq(email)).fetchOne(USERS.ID)
            ),
        )

    @Test
    fun `a fresh account is read-only until the link is opened`() {
        val user = registerUnverifiedUser("Unconfirmed Resident")

        assertThat(json(getJson("/api/v1/auth/me", user.headers))["user"]["emailVerified"].asBoolean()).isFalse()
        assertThat(issuedLinks(user.email)).describedAs("registration issued a link").isEqualTo(1)

        val pinned = sendJson(
            HttpMethod.POST, "/api/v1/events",
            mapOf(
                "type" to "help",
                "title" to "Should not stick",
                "body" to "Written before confirming.",
                "location" to mapOf("lng" to 12.1, "lat" to 55.6),
                "applyable" to false,
                "expiresAt" to Instant.now().plus(10, ChronoUnit.DAYS).toString(),
            ),
            user.headers,
        )
        assertThat(pinned.statusCode.value()).isEqualTo(403)
        assertThat(json(pinned)["code"].asText()).isEqualTo("email_unverified")

        assertThat(getJson("/api/v1/events?bbox=12.0,55.5,12.2,55.7&zoom=13", user.headers).statusCode.value())
            .describedAs("reading the board stays open")
            .isEqualTo(200)

        assertThat(sendJson(HttpMethod.POST, "/api/v1/auth/logout", null, user.headers).statusCode.value())
            .describedAs("signing out is not a board write")
            .isEqualTo(204)
    }

    @Test
    fun `opening the link confirms the account and hands the board back`() {
        val user = registerUnverifiedUser("Confirming Resident")
        val token = verifications.issue(user.id, user.email, "Confirming Resident")

        val redirect = openLink(token)
        assertThat(redirect.statusCode()).isEqualTo(302)
        assertThat(location(redirect)).endsWith("/?verified=1")

        assertThat(json(getJson("/api/v1/auth/me", user.headers))["user"]["emailVerified"].asBoolean()).isTrue()
        assertThat(createEvent(user, 12.15, 55.65, title = "Now it sticks")).isNotBlank()

        assertThat(location(openLink(token)))
            .describedAs("a second visit is not an error")
            .endsWith("/?verified=already")
    }

    @Test
    fun `an expired or unknown link is turned away`() {
        val user = registerUnverifiedUser("Slow Resident")
        val token = verifications.issue(user.id, user.email, "Slow Resident")
        dsl.update(EMAIL_VERIFICATIONS)
            .set(EMAIL_VERIFICATIONS.EXPIRES_AT, OffsetDateTime.now().minusHours(1))
            .where(EMAIL_VERIFICATIONS.USER_ID.eq(user.id))
            .execute()

        assertThat(location(openLink(token))).endsWith("/?verified=expired")
        assertThat(location(openLink("not-a-token"))).endsWith("/?verified=invalid")
        assertThat(json(getJson("/api/v1/auth/me", user.headers))["user"]["emailVerified"].asBoolean()).isFalse()
    }

    @Test
    fun `an unconfirmed account can ask for another link, within reason`() {
        val user = registerUnverifiedUser("Impatient Resident")

        val resent = sendJson(HttpMethod.POST, "/api/v1/auth/verification/resend", null, user.headers)
        assertThat(resent.statusCode.value()).isEqualTo(202)
        assertThat(issuedLinks(user.email)).isEqualTo(2)

        val second = sendJson(HttpMethod.POST, "/api/v1/auth/verification/resend", null, user.headers)
        val third = sendJson(HttpMethod.POST, "/api/v1/auth/verification/resend", null, user.headers)
        assertThat(listOf(second, third).map { it.statusCode.value() })
            .describedAs("resending is rate limited")
            .contains(429)
    }
}
