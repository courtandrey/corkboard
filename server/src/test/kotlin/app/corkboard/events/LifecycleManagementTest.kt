package app.corkboard.events

import app.corkboard.ApiTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod

class LifecycleManagementTest : ApiTestBase() {

    @Test
    fun `delete is a soft removal visible only to the author`() {
        val author = registerUser("Deleter")
        val stranger = registerUser()
        val id = createEvent(author, 39.3001, 57.1501, title = "Regrettable note")
        val viewport = "/api/v1/events?bbox=39.20,57.10,39.40,57.20&zoom=14"

        val denied = rest.exchange(
            "/api/v1/events/$id", HttpMethod.DELETE,
            HttpEntity<Void>(stranger.headers), String::class.java,
        )
        assertThat(denied.statusCode.value()).isEqualTo(403)

        val removed = rest.exchange(
            "/api/v1/events/$id", HttpMethod.DELETE,
            HttpEntity<Void>(author.headers), String::class.java,
        )
        assertThat(removed.statusCode.value()).isEqualTo(204)

        assertThat(json(getJson(viewport))["items"].map { it["id"].asText() }).doesNotContain(id)
        assertThat(getJson("/api/v1/events/$id", stranger.headers).statusCode.value()).isEqualTo(404)
        val authorView = getJson("/api/v1/events/$id", author.headers)
        assertThat(authorView.statusCode.value()).isEqualTo(200)
        assertThat(json(authorView)["status"].asText()).isEqualTo("removed")
    }

    @Test
    fun `me-events lists every status with filter and cursor`() {
        val author = registerUser("Manager")
        val active = createEvent(author, 39.5, 57.3, title = "Still up")
        val resolved = createEvent(author, 39.51, 57.31, title = "Sorted out")
        val removed = createEvent(author, 39.52, 57.32, title = "Taken down")
        sendJson(HttpMethod.POST, "/api/v1/events/$resolved/resolve", null, author.headers)
        rest.exchange(
            "/api/v1/events/$removed", HttpMethod.DELETE,
            HttpEntity<Void>(author.headers), String::class.java,
        )

        val all = json(getJson("/api/v1/me/events", author.headers))["items"]
        assertThat(all.map { it["id"].asText() }).containsExactlyInAnyOrder(active, resolved, removed)
        assertThat(all.first { it["id"].asText() == resolved }["status"].asText()).isEqualTo("resolved")
        assertThat(all.first { it["id"].asText() == resolved }["resolvedAt"].isNull).isFalse

        val onlyRemoved = json(getJson("/api/v1/me/events?status=removed", author.headers))["items"]
        assertThat(onlyRemoved.map { it["id"].asText() }).containsExactly(removed)

        val firstPage = json(getJson("/api/v1/me/events?limit=2", author.headers))
        assertThat(firstPage["items"]).hasSize(2)
        val cursor = firstPage["nextCursor"].asText()
        assertThat(cursor).isNotBlank
        val secondPage = json(getJson("/api/v1/me/events?limit=2&cursor=$cursor", author.headers))
        assertThat(secondPage["items"]).hasSize(1)
        val seen = firstPage["items"].map { it["id"].asText() } + secondPage["items"].map { it["id"].asText() }
        assertThat(seen).containsExactlyInAnyOrder(active, resolved, removed)
    }
}
