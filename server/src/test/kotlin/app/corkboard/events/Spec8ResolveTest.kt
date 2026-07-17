package app.corkboard.events

import app.corkboard.ApiTestBase
import app.corkboard.MutableClock
import app.corkboard.MutableClockConfig
import java.time.Duration
import java.time.temporal.ChronoUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod

@Import(MutableClockConfig::class)
class Spec8ResolveTest : ApiTestBase() {

    @Autowired
    lateinit var clock: MutableClock

    private fun createEvent(user: app.corkboard.TestUser, lng: Double, lat: Double): String {
        val res = sendJson(
            HttpMethod.POST, "/api/v1/events",
            mapOf(
                "type" to "lost_found",
                "title" to "Found: one glove, surprisingly nice",
                "body" to "Leather, left hand, gate of the park.",
                "location" to mapOf("lng" to lng, "lat" to lat),
                "applyable" to true,
                "expiresAt" to clock.instant().plus(30, ChronoUnit.DAYS).toString(),
            ),
            user.headers,
        )
        check(res.statusCode.value() == 201) { res.body ?: "" }
        return json(res)["id"].asText()
    }

    @Test
    fun `resolve stamps the note, keeps it visible 48 hours, then retires it`() {
        val author = registerUser("Resolver")
        val stranger = registerUser()
        val id = createEvent(author, 37.3001, 55.7501)
        val viewport = "/api/v1/events?bbox=37.20,55.70,37.40,55.80&zoom=14"

        assertThat(json(getJson(viewport))["items"].map { it["id"].asText() }).contains(id)

        val denied = sendJson(HttpMethod.POST, "/api/v1/events/$id/resolve", null, stranger.headers)
        assertThat(denied.statusCode.value()).isEqualTo(403)

        val resolved = sendJson(HttpMethod.POST, "/api/v1/events/$id/resolve", null, author.headers)
        assertThat(resolved.statusCode.value()).isEqualTo(200)
        assertThat(json(resolved)["status"].asText()).isEqualTo("resolved")
        assertThat(json(resolved)["resolvedAt"].isNull).isFalse

        assertThat(json(getJson(viewport))["items"].map { it["id"].asText() })
            .describedAs("stamped-resolved note stays on the board inside the 48h window")
            .contains(id)
        assertThat(getJson("/api/v1/events/$id").statusCode.value()).isEqualTo(200)

        clock.advance(Duration.ofHours(49))

        assertThat(json(getJson(viewport))["items"].map { it["id"].asText() })
            .describedAs("after 48h the resolved note leaves the board")
            .doesNotContain(id)
        assertThat(getJson("/api/v1/events/$id").statusCode.value()).isEqualTo(200)

        val again = sendJson(HttpMethod.POST, "/api/v1/events/$id/resolve", null, author.headers)
        assertThat(again.statusCode.value()).isEqualTo(409)
        assertThat(json(again)["code"].asText()).isEqualTo("invalid_status")
    }
}
