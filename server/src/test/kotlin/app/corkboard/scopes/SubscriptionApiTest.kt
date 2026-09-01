package app.corkboard.scopes

import app.corkboard.ApiTestBase
import app.corkboard.TestUser
import app.corkboard.features.FeatureFlag
import app.corkboard.features.FeatureFlagService
import app.corkboard.jooq.tables.references.FEATURE_FLAGS
import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod

private const val WORLD = "bbox=-180,-85,180,85&zoom=2&clustered=false"

class SubscriptionApiTest : ApiTestBase() {

    @Autowired
    lateinit var flags: FeatureFlagService

    @AfterEach
    fun restoreFlag() {
        dsl.deleteFrom(FEATURE_FLAGS)
            .where(FEATURE_FLAGS.KEY.eq(FeatureFlag.IS_SUBSCRIPTION_ENABLED.name))
            .execute()
        flags.refresh()
    }

    private fun idOf(user: TestUser): String =
        json(getJson("/api/v1/auth/me", user.headers))["user"]["id"].asText()

    private fun connect(one: TestUser, other: TestUser) {
        val id = json(
            sendJson(HttpMethod.POST, "/api/v1/connections", mapOf("userId" to idOf(other)), one.headers),
        )["id"].asText()
        val accepted = sendJson(HttpMethod.POST, "/api/v1/connections/$id/accept", null, other.headers)
        check(accepted.statusCode.value() == 200) { accepted.body ?: "" }
    }

    private fun share(owner: TestUser, viewer: TestUser) =
        sendJson(
            HttpMethod.POST, "/api/v1/subscriptions/viewers",
            mapOf("userId" to idOf(viewer)), owner.headers,
        )

    private fun personalNote(owner: TestUser, title: String, lng: Double, lat: Double): String {
        val res = sendJson(
            HttpMethod.POST, "/api/v1/boards/${idOf(owner)}/events",
            mapOf(
                "type" to "plan",
                "title" to title,
                "body" to "Written on my own board.",
                "location" to mapOf("lng" to lng, "lat" to lat),
                "applyable" to false,
                "expiresAt" to Instant.now().plus(20, ChronoUnit.DAYS).toString(),
                "tags" to emptyList<String>(),
            ),
            owner.headers,
        )
        check(res.statusCode.value() == 201) { res.body ?: "" }
        return json(res)["id"].asText()
    }

    private fun feed(user: TestUser, extra: String = ""): JsonNode =
        json(getJson("/api/v1/subscriptions/events?$WORLD$extra", user.headers))

    @Test
    fun `a board is only fed to the people its owner let in`() {
        val owner = registerUser("Feed Owner")
        val friend = registerUser("Feed Friend")
        val stranger = registerUser("Feed Stranger")
        val noteId = personalNote(owner, "Sunday walk", 21.1, 51.1)

        assertThat(feed(friend)["items"]).describedAs("nothing shared, nothing fed").isEmpty()
        assertThat(getJson("/api/v1/subscriptions/events/$noteId", friend.headers).statusCode.value())
            .isEqualTo(404)

        val refused = share(owner, friend)
        assertThat(refused.statusCode.value()).describedAs("only people you know").isEqualTo(403)
        assertThat(json(refused)["code"].asText()).isEqualTo("not_connected")

        connect(owner, friend)
        assertThat(share(owner, friend).statusCode.value()).isEqualTo(204)

        assertThat(feed(friend)["items"].map { it["title"].asText() }).containsExactly("Sunday walk")
        assertThat(json(getJson("/api/v1/subscriptions/events/$noteId", friend.headers))["title"].asText())
            .isEqualTo("Sunday walk")

        connect(owner, stranger)
        assertThat(feed(stranger)["items"]).describedAs("knowing somebody is not seeing their board").isEmpty()

        assertThat(json(getJson("/api/v1/subscriptions", friend.headers))["following"].map { it["displayName"].asText() })
            .containsExactly("Feed Owner")
        assertThat(json(getJson("/api/v1/subscriptions", owner.headers))["viewers"].map { it["displayName"].asText() })
            .containsExactly("Feed Friend")
    }

    @Test
    fun `the feed can be narrowed to one person, and taking access back empties it`() {
        val one = registerUser("Feed One")
        val other = registerUser("Feed Other")
        val reader = registerUser("Feed Reader")
        personalNote(one, "One's plan", 22.1, 52.1)
        personalNote(other, "Other's plan", 22.2, 52.2)

        connect(one, reader)
        connect(other, reader)
        share(one, reader)
        share(other, reader)

        assertThat(feed(reader)["items"].map { it["title"].asText() })
            .containsExactlyInAnyOrder("One's plan", "Other's plan")
        assertThat(feed(reader, "&owners=${idOf(one)}")["items"].map { it["title"].asText() })
            .containsExactly("One's plan")

        val removed = rest.exchange(
            "/api/v1/subscriptions/viewers/${idOf(reader)}", HttpMethod.DELETE,
            org.springframework.http.HttpEntity<Void>(one.headers), String::class.java,
        )
        assertThat(removed.statusCode.value()).isEqualTo(204)
        assertThat(feed(reader)["items"].map { it["title"].asText() }).containsExactly("Other's plan")
    }

    @Test
    fun `a subscriber can answer a personal note, and a stranger cannot`() {
        val owner = registerUser("Answerable Owner")
        val friend = registerUser("Answerable Friend")
        val stranger = registerUser("Answerable Stranger")
        val noteId = personalNote(owner, "Help me move the piano", 23.1, 53.1)
        connect(owner, friend)
        share(owner, friend)

        val detail = json(getJson("/api/v1/subscriptions/events/$noteId", friend.headers))
        assertThat(detail["viewerState"]["canRespond"].asBoolean())
            .describedAs("the note was shared with them, so it can be answered")
            .isTrue

        val answered = sendJson(
            HttpMethod.POST, "/api/v1/events/$noteId/apply",
            mapOf("message" to "I have a van on Saturday."), friend.headers,
        )
        assertThat(answered.statusCode.value()).isEqualTo(201)

        val refused = sendJson(
            HttpMethod.POST, "/api/v1/events/$noteId/apply",
            mapOf("message" to "Me too."), stranger.headers,
        )
        assertThat(refused.statusCode.value()).isEqualTo(403)

        val received = json(getJson("/api/v1/me/applications?role=received", owner.headers))
        assertThat(received["items"].flatMap { it["applications"] }.map { it["message"].asText() })
            .contains("I have a van on Saturday.")
    }

    @Test
    fun `switching the feature off closes the feed and the answering with it`() {
        val owner = registerUser("Toggled Owner")
        val friend = registerUser("Toggled Friend")
        val noteId = personalNote(owner, "Quiet plan", 24.1, 54.1)
        connect(owner, friend)
        share(owner, friend)

        flags.set(FeatureFlag.IS_SUBSCRIPTION_ENABLED, false, owner.id)

        val closed = getJson("/api/v1/subscriptions/events?$WORLD", friend.headers)
        assertThat(closed.statusCode.value()).isEqualTo(403)
        assertThat(json(closed)["code"].asText()).isEqualTo("feature_disabled")

        assertThat(getJson("/api/v1/boards/${idOf(owner)}/events?$WORLD", friend.headers).statusCode.value())
            .describedAs("the board itself is private again")
            .isEqualTo(403)

        val answered = sendJson(
            HttpMethod.POST, "/api/v1/events/$noteId/apply",
            mapOf("message" to "Still here?"), friend.headers,
        )
        assertThat(answered.statusCode.value()).isEqualTo(404)
    }
}
