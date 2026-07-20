package app.corkboard.events

import app.corkboard.ApiTestBase
import app.corkboard.TestUser
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
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
    fun `notes sharing a spot merge into one counted cluster at every zoom`() {
        val user = registerUser()
        val ids = (1..5).map { createEvent(user, lng = 10.0001, lat = 50.0001, title = "Stacked note $it") }
        val bbox = "9.85,49.90,10.15,50.10"

        for (zoom in listOf(13, 18)) {
            val res = json(getJson("/api/v1/events?bbox=$bbox&zoom=$zoom"))
            assertThat(res["items"].map { it["id"].asText() }.filter { it in ids }).isEmpty()
            val cluster = res["clusters"].first { it["count"].asInt() >= 5 }
            assertThat(cluster["location"]["lng"].asDouble()).isCloseTo(10.0001, within(1e-6))
            assertThat(cluster["bounds"]["east"].asDouble() - cluster["bounds"]["west"].asDouble())
                .isLessThan(1e-9)
            assertThat(res["total"].asInt()).isGreaterThanOrEqualTo(5)
        }

        val members = json(
            getJson("/api/v1/events?bbox=10.0000,50.0000,10.0002,50.0002&zoom=18&clustered=false")
        )
        assertThat(members["items"].map { it["id"].asText() }).containsAll(ids)
        assertThat(members["clusters"]).isEmpty()
    }

    @Test
    fun `nearby notes merge at low zoom and split apart when zooming in`() {
        val user = registerUser()
        val a = createEvent(user, lng = 22.5010, lat = 45.8990, title = "West neighbor")
        val b = createEvent(user, lng = 22.5040, lat = 45.8990, title = "East neighbor")
        val bbox = "22.45,45.85,22.55,45.95"

        val merged = json(getJson("/api/v1/events?bbox=$bbox&zoom=13"))
        assertThat(merged["items"].map { it["id"].asText() }).doesNotContain(a, b)
        assertThat(merged["clusters"].map { it["count"].asInt() }).contains(2)

        val split = json(getJson("/api/v1/events?bbox=$bbox&zoom=16"))
        assertThat(split["items"].map { it["id"].asText() }).contains(a, b)
    }

    @Test
    fun `clusters are identical across overlapping viewports`() {
        val user = registerUser()
        createEvent(user, lng = 23.5010, lat = 46.2010, title = "Stable one")
        createEvent(user, lng = 23.5025, lat = 46.2015, title = "Stable two")
        createEvent(user, lng = 23.5040, lat = 46.2020, title = "Stable three")

        val panned = listOf(
            "23.40,46.10,23.60,46.30",
            "23.45,46.15,23.65,46.35",
            "23.20,46.05,23.55,46.25",
        ).map { bbox ->
            json(getJson("/api/v1/events?bbox=$bbox&zoom=13"))["clusters"]
                .first { it["count"].asInt() == 3 }
        }

        for (cluster in panned.drop(1)) {
            assertThat(cluster["location"]["lng"].asDouble())
                .isEqualTo(panned[0]["location"]["lng"].asDouble())
            assertThat(cluster["location"]["lat"].asDouble())
                .isEqualTo(panned[0]["location"]["lat"].asDouble())
            assertThat(cluster["bounds"]).isEqualTo(panned[0]["bounds"])
        }
    }

    @Test
    fun `antimeridian viewport sees both sides of the seam`() {
        val user = registerUser()
        val fiji = createEvent(user, lng = 179.95, lat = -60.5, title = "East of the seam")
        val chatham = createEvent(user, lng = -179.95, lat = -60.5, title = "West of the seam")

        val res = json(getJson("/api/v1/events?bbox=179.5,-61.0,-179.5,-60.0&zoom=10"))
        assertThat(res["items"].map { it["id"].asText() }).contains(fiji, chatham)
        assertThat(res["total"].asInt()).isGreaterThanOrEqualTo(2)
    }

    @Test
    fun `grid resolution is bounded by the requested span, not the requested zoom`() {
        val world = "-180,-85,180,85"
        val coarse = json(getJson("/api/v1/events?bbox=$world&zoom=2"))
        val mismatched = json(getJson("/api/v1/events?bbox=$world&zoom=14"))

        assertThat(mismatched["total"].asInt()).isEqualTo(coarse["total"].asInt())
        assertThat(mismatched["clusters"].size()).isEqualTo(coarse["clusters"].size())
        assertThat(mismatched["items"].size()).isEqualTo(coarse["items"].size())
        assertThat(mismatched["clusters"].size() + mismatched["items"].size())
            .isLessThanOrEqualTo(46 * 46)
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
