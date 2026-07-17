package app.corkboard.moderation

import app.corkboard.ApiTestBase
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod

class Spec5HideTest : ApiTestBase() {

    @Test
    fun `hide removes the note from the hider's board only`() {
        val author = registerUser("Hide Author")
        val hider = registerUser("Hider")
        val bystander = registerUser("Bystander")
        val bbox = "31.20,61.10,31.40,61.20"

        val created = sendJson(
            HttpMethod.POST, "/api/v1/events",
            mapOf(
                "type" to "happening",
                "title" to "Accordion practice, nightly",
                "body" to "Every night. All night. You know the apartment.",
                "location" to mapOf("lng" to 31.3001, "lat" to 61.1501),
                "applyable" to false,
                "expiresAt" to Instant.now().plus(15, ChronoUnit.DAYS).toString(),
            ),
            author.headers,
        )
        val id = json(created)["id"].asText()
        val viewport = "/api/v1/events?bbox=$bbox&zoom=14"

        assertThat(sendJson(HttpMethod.POST, "/api/v1/events/$id/hide", null, hider.headers).statusCode.value())
            .isEqualTo(204)

        assertThat(json(getJson(viewport, hider.headers))["items"].map { it["id"].asText() }).doesNotContain(id)
        assertThat(json(getJson(viewport, bystander.headers))["items"].map { it["id"].asText() }).contains(id)
        assertThat(json(getJson(viewport))["items"].map { it["id"].asText() }).contains(id)

        assertThat(json(getJson("/api/v1/events/$id", hider.headers))["viewerState"]["hidden"].asBoolean()).isTrue

        assertThat(
            rest.exchange(
                "/api/v1/events/$id/hide", HttpMethod.DELETE,
                org.springframework.http.HttpEntity<Void>(hider.headers), String::class.java,
            ).statusCode.value()
        ).isEqualTo(204)
        assertThat(json(getJson(viewport, hider.headers))["items"].map { it["id"].asText() }).contains(id)
    }
}
