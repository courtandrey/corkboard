package app.corkboard.events

import app.corkboard.ApiTestBase
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod

class Spec1ViewportTest : ApiTestBase() {

    @Test
    fun `created note appears in its viewport and nowhere else`() {
        val user = registerUser("Spec One")
        val created = sendJson(
            HttpMethod.POST, "/api/v1/events",
            mapOf(
                "type" to "lost_found",
                "title" to "Missing cat Pirozhok",
                "body" to "Grey tabby, red collar, last seen near Tompkins Square.",
                "location" to mapOf("lng" to -73.9816, "lat" to 40.7265),
                "applyable" to true,
                "expiresAt" to Instant.now().plus(30, ChronoUnit.DAYS).toString(),
                "tags" to listOf("cats", "east-village"),
            ),
            user.headers,
        )
        assertThat(created.statusCode.value()).isEqualTo(201)
        val id = json(created)["id"].asText()
        assertThat(json(created)["tags"].map { it["slug"].asText() }).containsExactly("cats", "east-village")
        assertThat(json(created)["viewerState"]["isAuthor"].asBoolean()).isTrue

        val inside = getJson("/api/v1/events?bbox=-74.05,40.65,-73.90,40.80&zoom=13")
        assertThat(inside.statusCode.value()).isEqualTo(200)
        assertThat(json(inside)["items"].map { it["id"].asText() }).contains(id)

        val outside = getJson("/api/v1/events?bbox=4.30,51.85,4.60,52.00&zoom=13")
        assertThat(json(outside)["items"].map { it["id"].asText() }).doesNotContain(id)
    }

    @Test
    fun `create enforces the 90-day expiry ceiling`() {
        val user = registerUser()
        val res = sendJson(
            HttpMethod.POST, "/api/v1/events",
            mapOf(
                "type" to "notice",
                "title" to "Too far in the future",
                "body" to "This note wants to outlive the board.",
                "location" to mapOf("lng" to -73.99, "lat" to 40.73),
                "applyable" to false,
                "expiresAt" to Instant.now().plus(120, ChronoUnit.DAYS).toString(),
            ),
            user.headers,
        )
        assertThat(res.statusCode.value()).isEqualTo(422)
        assertThat(json(res)["code"].asText()).isEqualTo("expiry_too_far")
    }

    @Test
    fun `detail exposes author card without email and anonymous viewer state`() {
        val user = registerUser("Cardholder")
        val created = sendJson(
            HttpMethod.POST, "/api/v1/events",
            mapOf(
                "type" to "giveaway",
                "title" to "Bookshelf, self-pickup",
                "body" to "Solid pine, slightly scratched. First come, first served.",
                "location" to mapOf("lng" to -73.95, "lat" to 40.68),
                "applyable" to true,
                "expiresAt" to Instant.now().plus(10, ChronoUnit.DAYS).toString(),
            ),
            user.headers,
        )
        val id = json(created)["id"].asText()

        val detail = getJson("/api/v1/events/$id")
        assertThat(detail.statusCode.value()).isEqualTo(200)
        val body = json(detail)
        assertThat(body["author"]["displayName"].asText()).isEqualTo("Cardholder")
        assertThat(body["author"].has("email")).isFalse
        assertThat(body["viewerState"]["isAuthor"].asBoolean()).isFalse
        assertThat(body["viewerState"]["applied"].asBoolean()).isFalse
    }
}
