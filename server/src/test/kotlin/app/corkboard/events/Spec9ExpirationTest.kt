package app.corkboard.events

import app.corkboard.ApiTestBase
import app.corkboard.MutableClock
import app.corkboard.MutableClockConfig
import app.corkboard.TestUser
import app.corkboard.jobs.ExpirationSweep
import java.time.Duration
import java.time.temporal.ChronoUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod

@Import(MutableClockConfig::class)
class Spec9ExpirationTest : ApiTestBase() {

    @Autowired
    lateinit var clock: MutableClock

    @Autowired
    lateinit var sweep: ExpirationSweep

    private fun createEvent(user: TestUser, lng: Double, lat: Double, expiresInDays: Long, title: String): String {
        val res = sendJson(
            HttpMethod.POST, "/api/v1/events",
            mapOf(
                "type" to "happening",
                "title" to title,
                "body" to "Body of $title.",
                "location" to mapOf("lng" to lng, "lat" to lat),
                "applyable" to false,
                "expiresAt" to clock.instant().plus(expiresInDays, ChronoUnit.DAYS).toString(),
            ),
            user.headers,
        )
        check(res.statusCode.value() == 201) { res.body ?: "" }
        return json(res)["id"].asText()
    }

    @Test
    fun `sweep warns authors, flips overdue notes, and renew revives them`() {
        val author = registerUser("Sweep Author")
        val longLived = createEvent(author, 38.3001, 56.1501, expiresInDays = 30, title = "Long-lived note")
        val shortLived = createEvent(author, 38.3002, 56.1502, expiresInDays = 2, title = "Short-lived note")
        val viewport = "/api/v1/events?bbox=38.20,56.10,38.40,56.20&zoom=14"

        sweep.sweep()
        val expiringNotes = json(getJson("/api/v1/notifications", author.headers))["items"]
            .filter { it["kind"].asText() == "event_expiring" }
        assertThat(expiringNotes.map { it["payload"]["eventId"].asText() }).containsExactly(shortLived)

        sweep.sweep()
        val afterSecond = json(getJson("/api/v1/notifications", author.headers))["items"]
            .filter { it["kind"].asText() == "event_expiring" }
        assertThat(afterSecond).describedAs("expiring warning fires once per event").hasSize(1)

        clock.advance(Duration.ofDays(3))
        sweep.sweep()

        val mine = json(getJson("/api/v1/me/events", author.headers))["items"]
        assertThat(mine.first { it["id"].asText() == shortLived }["status"].asText()).isEqualTo("expired")
        assertThat(mine.first { it["id"].asText() == longLived }["status"].asText()).isEqualTo("active")

        val ids = json(getJson(viewport))["items"].map { it["id"].asText() }
        assertThat(ids).contains(longLived).doesNotContain(shortLived)

        val renewed = sendJson(
            HttpMethod.POST, "/api/v1/events/$shortLived/renew",
            mapOf("expiresAt" to clock.instant().plus(30, ChronoUnit.DAYS).toString()),
            author.headers,
        )
        assertThat(renewed.statusCode.value()).isEqualTo(200)
        assertThat(json(renewed)["status"].asText()).isEqualTo("active")

        assertThat(json(getJson(viewport))["items"].map { it["id"].asText() }).contains(shortLived)
    }

    @Test
    fun `renew is rejected for removed notes and enforces the ceiling`() {
        val author = registerUser()
        val id = createEvent(author, 38.5, 56.3, expiresInDays = 10, title = "Doomed note")

        val tooFar = sendJson(
            HttpMethod.POST, "/api/v1/events/$id/renew",
            mapOf("expiresAt" to clock.instant().plus(120, ChronoUnit.DAYS).toString()),
            author.headers,
        )
        assertThat(tooFar.statusCode.value()).isEqualTo(422)
        assertThat(json(tooFar)["code"].asText()).isEqualTo("expiry_too_far")

        rest.exchange(
            "/api/v1/events/$id", HttpMethod.DELETE,
            org.springframework.http.HttpEntity<Void>(author.headers), String::class.java,
        )
        val afterRemoval = sendJson(
            HttpMethod.POST, "/api/v1/events/$id/renew",
            mapOf("expiresAt" to clock.instant().plus(30, ChronoUnit.DAYS).toString()),
            author.headers,
        )
        assertThat(afterRemoval.statusCode.value()).isEqualTo(409)
        assertThat(json(afterRemoval)["code"].asText()).isEqualTo("invalid_status")
    }
}
