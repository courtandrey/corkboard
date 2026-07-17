package app.corkboard.events

import app.corkboard.ApiTestBase
import app.corkboard.TestUser
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod

class ViewportQueryTest : ApiTestBase() {

    private fun createEvent(
        user: TestUser,
        lng: Double,
        lat: Double,
        type: String = "notice",
        title: String = "Grid filler note",
        applyable: Boolean = false,
        tags: List<String> = emptyList(),
    ): String {
        val res = sendJson(
            HttpMethod.POST, "/api/v1/events",
            mapOf(
                "type" to type,
                "title" to title,
                "body" to "Body of $title.",
                "location" to mapOf("lng" to lng, "lat" to lat),
                "applyable" to applyable,
                "expiresAt" to Instant.now().plus(20, ChronoUnit.DAYS).toString(),
                "tags" to tags,
            ),
            user.headers,
        )
        check(res.statusCode.value() == 201) { res.body ?: "" }
        return json(res)["id"].asText()
    }

    @Test
    fun `per-cell cap keeps a dense cluster from monopolizing the board`() {
        val user = registerUser()
        val ids = (1..5).map { createEvent(user, lng = 10.0001, lat = 50.0001, title = "Cluster note $it") }
        val bbox = "9.85,49.90,10.15,50.10"

        val lowZoom = json(getJson("/api/v1/events?bbox=$bbox&zoom=13"))
        assertThat(lowZoom["items"].map { it["id"].asText() }.filter { it in ids }).hasSize(3)
        assertThat(lowZoom["total"].asInt()).isGreaterThanOrEqualTo(5)

        val highZoom = json(getJson("/api/v1/events?bbox=$bbox&zoom=15"))
        assertThat(highZoom["items"].map { it["id"].asText() }.filter { it in ids }).hasSize(5)
    }

    @Test
    fun `antimeridian viewport clamps to the western box and flags truncation`() {
        val res = json(getJson("/api/v1/events?bbox=170,-10,-170,10&zoom=13"))
        assertThat(res["truncated"].asBoolean()).isTrue
    }

    @Test
    fun `type applyable and text filters compose`() {
        val user = registerUser()
        val club = createEvent(user, 20.3001, 44.8001, type = "club", title = "Chess evenings", tags = listOf("chess"))
        val giveaway = createEvent(user, 20.3002, 44.8002, type = "giveaway", title = "Free sourdough starter", applyable = true)
        val bbox = "20.20,44.75,20.40,44.85"

        val byType = json(getJson("/api/v1/events?bbox=$bbox&zoom=14&types=club"))
        assertThat(byType["items"].map { it["id"].asText() }).contains(club).doesNotContain(giveaway)

        val byApplyable = json(getJson("/api/v1/events?bbox=$bbox&zoom=14&applyable=true"))
        assertThat(byApplyable["items"].map { it["id"].asText() }).contains(giveaway).doesNotContain(club)

        val byTag = json(getJson("/api/v1/events?bbox=$bbox&zoom=14&tags=chess"))
        assertThat(byTag["items"].map { it["id"].asText() }).contains(club).doesNotContain(giveaway)

        val byText = json(getJson("/api/v1/events?bbox=$bbox&zoom=14&q=sourdough"))
        assertThat(byText["items"].map { it["id"].asText() }).contains(giveaway).doesNotContain(club)
    }

    @Test
    fun `malformed bbox is rejected with a fields map`() {
        val res = getJson("/api/v1/events?bbox=oops&zoom=13")
        assertThat(res.statusCode.value()).isEqualTo(422)
        assertThat(json(res)["code"].asText()).isEqualTo("validation_failed")
        assertThat(json(res)["fields"].has("bbox")).isTrue

        val outOfRange = getJson("/api/v1/events?bbox=-200,40,-73,41&zoom=13")
        assertThat(outOfRange.statusCode.value()).isEqualTo(422)

        val unknownType = getJson("/api/v1/events?bbox=-74,40,-73,41&zoom=13&types=party")
        assertThat(unknownType.statusCode.value()).isEqualTo(422)
    }
}
