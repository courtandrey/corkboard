package app.corkboard.events

import app.corkboard.ApiTestBase
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod

class Spec1ViewportTest : ApiTestBase() {

    @Autowired
    lateinit var sweep: app.corkboard.jobs.ExpirationSweep

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
    fun `an end date may be as far off as the author likes, but not in the past`() {
        val user = registerUser()

        fun pin(title: String, expiresAt: String?) = sendJson(
            HttpMethod.POST, "/api/v1/events",
            buildMap {
                put("type", "notice")
                put("title", title)
                put("body", "How long it stays is the author's business.")
                put("location", mapOf("lng" to -73.99, "lat" to 40.73))
                put("applyable", false)
                expiresAt?.let { put("expiresAt", it) }
            },
            user.headers,
        )

        val distant = pin("Ten years out", Instant.now().plus(3650, ChronoUnit.DAYS).toString())
        assertThat(distant.statusCode.value()).describedAs("no ceiling any more").isEqualTo(201)

        val past = pin("Already over", Instant.now().minus(1, ChronoUnit.DAYS).toString())
        assertThat(past.statusCode.value()).describedAs("the past is still refused").isEqualTo(422)
    }

    @Test
    fun `a note with no end date stays on the board and is never swept`() {
        val user = registerUser()
        val res = sendJson(
            HttpMethod.POST, "/api/v1/events",
            mapOf(
                "type" to "notice",
                "title" to "Here until I say otherwise",
                "body" to "No end date at all.",
                "location" to mapOf("lng" to 100.512, "lat" to 13.741),
                "applyable" to false,
            ),
            user.headers,
        )
        assertThat(res.statusCode.value()).isEqualTo(201)
        val id = json(res)["id"].asText()
        assertThat(json(res)["expiresAt"].isNull).describedAs("the contract says so outright").isTrue()

        val board = getJson("/api/v1/events?bbox=100.50,13.73,100.53,13.75&zoom=18")
        assertThat(json(board)["items"].map { it["id"].asText() }).contains(id)

        sweep.sweep()

        assertThat(json(getJson("/api/v1/events/$id"))["status"].asText())
            .describedAs("a comparison against NULL is never true, so the sweep passes it by")
            .isEqualTo("active")
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
