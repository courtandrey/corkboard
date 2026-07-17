package app.corkboard.moderation

import app.corkboard.ApiTestBase
import app.corkboard.TestUser
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod

class Spec2VoteTest : ApiTestBase() {

    private fun createEvent(user: TestUser, title: String, lng: Double, lat: Double): String {
        val res = sendJson(
            HttpMethod.POST, "/api/v1/events",
            mapOf(
                "type" to "notice",
                "title" to title,
                "body" to "Body of $title.",
                "location" to mapOf("lng" to lng, "lat" to lat),
                "applyable" to false,
                "expiresAt" to Instant.now().plus(15, ChronoUnit.DAYS).toString(),
            ),
            user.headers,
        )
        check(res.statusCode.value() == 201) { res.body ?: "" }
        return json(res)["id"].asText()
    }

    @Test
    fun `vote toggles the score and drives viewport ranking`() {
        val author = registerUser("Vote Author")
        val voter = registerUser("Voter")
        val bbox = "30.20,60.10,30.40,60.20"
        val plain = createEvent(author, "Unloved note", 30.3001, 60.1501)
        val loved = createEvent(author, "Beloved note", 30.3002, 60.1502)

        val voted = sendJson(HttpMethod.POST, "/api/v1/events/$loved/vote", null, voter.headers)
        assertThat(voted.statusCode.value()).isEqualTo(200)
        assertThat(json(voted)["score"].asInt()).isEqualTo(1)
        assertThat(json(voted)["voted"].asBoolean()).isTrue

        val ranked = json(getJson("/api/v1/events?bbox=$bbox&zoom=14"))["items"].map { it["id"].asText() }
        assertThat(ranked.indexOf(loved)).isLessThan(ranked.indexOf(plain))

        val detail = json(getJson("/api/v1/events/$loved", voter.headers))
        assertThat(detail["viewerState"]["voted"].asBoolean()).isTrue
        assertThat(detail["score"].asInt()).isEqualTo(1)

        val unvoted = sendJson(HttpMethod.POST, "/api/v1/events/$loved/vote", null, voter.headers)
        assertThat(json(unvoted)["score"].asInt()).isEqualTo(0)
        assertThat(json(unvoted)["voted"].asBoolean()).isFalse
    }

    @Test
    fun `voting your own note is rejected`() {
        val author = registerUser()
        val id = createEvent(author, "Self-promotion attempt", 30.5, 60.3)
        val res = sendJson(HttpMethod.POST, "/api/v1/events/$id/vote", null, author.headers)
        assertThat(res.statusCode.value()).isEqualTo(409)
        assertThat(json(res)["code"].asText()).isEqualTo("own_event")
    }
}
