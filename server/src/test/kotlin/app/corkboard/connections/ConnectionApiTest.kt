package app.corkboard.connections

import app.corkboard.ApiTestBase
import com.fasterxml.jackson.databind.JsonNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod

class ConnectionApiTest : ApiTestBase() {

    private fun handleOf(user: org.springframework.http.HttpHeaders): String =
        json(getJson("/api/v1/auth/me", user))["user"]["handle"].asText()

    private fun idOf(user: HttpHeaders): String =
        json(getJson("/api/v1/auth/me", user))["user"]["id"].asText()

    private fun search(user: HttpHeaders, q: String): JsonNode =
        json(getJson("/api/v1/connections/people?q=$q", user))["items"]

    private fun connections(user: HttpHeaders): JsonNode = json(getJson("/api/v1/connections", user))

    @Test
    fun `someone is found by id or by name, and asking them raises one question they can answer`() {
        val asker = registerUser("Asking Neighbour")
        val asked = registerUser("Asked Neighbour")
        val askedId = idOf(asked.headers)
        val askedHandle = handleOf(asked.headers)

        val byHandle = search(asker.headers, askedHandle)
        assertThat(byHandle.map { it["id"].asText() }).contains(askedId)
        assertThat(byHandle.first { it["id"].asText() == askedId }["state"].asText()).isEqualTo("none")

        val byName = search(asker.headers, "Asked+Neigh")
        assertThat(byName.map { it["id"].asText() })
            .describedAs("a display name finds them too")
            .contains(askedId)

        val requested = sendJson(
            HttpMethod.POST, "/api/v1/connections", mapOf("userId" to askedId), asker.headers,
        )
        assertThat(requested.statusCode.value()).isEqualTo(201)
        val connectionId = json(requested)["id"].asText()

        assertThat(connections(asker.headers)["outgoing"].map { it["person"]["id"].asText() })
            .containsExactly(askedId)
        assertThat(connections(asked.headers)["incoming"].map { it["person"]["displayName"].asText() })
            .containsExactly("Asking Neighbour")

        val alert = json(getJson("/api/v1/notifications", asked.headers))["items"]
            .first { it["kind"].asText() == "connection_requested" }
        assertThat(alert["payload"]["connectionId"].asText()).isEqualTo(connectionId)
        assertThat(alert["payload"]["senderName"].asText()).isEqualTo("Asking Neighbour")

        val accepted = sendJson(
            HttpMethod.POST, "/api/v1/connections/$connectionId/accept", null, asked.headers,
        )
        assertThat(accepted.statusCode.value()).isEqualTo(200)
        assertThat(json(accepted)["state"].asText()).isEqualTo("connected")

        assertThat(connections(asker.headers)["connected"].map { it["person"]["id"].asText() })
            .containsExactly(askedId)
        assertThat(json(getJson("/api/v1/notifications", asker.headers))["items"].map { it["kind"].asText() })
            .describedAs("the asker hears back")
            .contains("connection_accepted")

        assertThat(search(asker.headers, askedHandle).first { it["id"].asText() == askedId }["state"].asText())
            .isEqualTo("connected")
    }

    @Test
    fun `dismissing takes the question away, and it can be asked again later`() {
        val asker = registerUser("Persistent Neighbour")
        val asked = registerUser("Unsure Neighbour")
        val askedId = idOf(asked.headers)

        val first = json(
            sendJson(HttpMethod.POST, "/api/v1/connections", mapOf("userId" to askedId), asker.headers),
        )["id"].asText()
        sendJson(HttpMethod.POST, "/api/v1/connections/$first/decline", null, asked.headers)

        assertThat(connections(asked.headers)["incoming"]).isEmpty()
        assertThat(connections(asker.headers)["outgoing"]).isEmpty()
        assertThat(json(getJson("/api/v1/notifications", asked.headers))["items"].map { it["kind"].asText() })
            .describedAs("dismissing clears the alert with it")
            .doesNotContain("connection_requested")

        val again = sendJson(
            HttpMethod.POST, "/api/v1/connections", mapOf("userId" to askedId), asker.headers,
        )
        assertThat(again.statusCode.value()).isEqualTo(201)
        assertThat(connections(asked.headers)["incoming"]).hasSize(1)
    }

    @Test
    fun `asking back is the same as saying yes, and asking twice is not`() {
        val one = registerUser("Mutual One")
        val other = registerUser("Mutual Other")

        sendJson(HttpMethod.POST, "/api/v1/connections", mapOf("userId" to idOf(other.headers)), one.headers)
        val second = sendJson(
            HttpMethod.POST, "/api/v1/connections", mapOf("userId" to idOf(one.headers)), other.headers,
        )

        assertThat(json(second)["state"].asText()).isEqualTo("connected")
        assertThat(connections(one.headers)["connected"]).hasSize(1)

        val third = sendJson(
            HttpMethod.POST, "/api/v1/connections", mapOf("userId" to idOf(other.headers)), one.headers,
        )
        assertThat(third.statusCode.value()).isEqualTo(409)
        assertThat(json(third)["code"].asText()).isEqualTo("already_connected")
    }

    @Test
    fun `only the person asked can answer`() {
        val asker = registerUser("Answer Asker")
        val asked = registerUser("Answer Asked")
        val stranger = registerUser("Answer Stranger")

        val id = json(
            sendJson(
                HttpMethod.POST, "/api/v1/connections",
                mapOf("userId" to idOf(asked.headers)), asker.headers,
            ),
        )["id"].asText()

        for (headers in listOf(asker.headers, stranger.headers)) {
            val res = sendJson(HttpMethod.POST, "/api/v1/connections/$id/accept", null, headers)
            assertThat(res.statusCode.value()).isEqualTo(404)
        }
    }

    @Test
    fun `anyone's card can be read, and it says where the reader stands with them`() {
        val reader = registerUser("Card Reader")
        val subject = registerUser("Card Subject")
        val subjectId = idOf(subject.headers)

        val toVisitor = json(getJson("/api/v1/users/$subjectId"))
        assertThat(toVisitor["displayName"].asText()).isEqualTo("Card Subject")
        assertThat(toVisitor["handle"].asText()).isEqualTo(handleOf(subject.headers))
        assertThat(toVisitor["memberSince"].isNull).isFalse
        assertThat(toVisitor["state"].asText())
            .describedAs("a signed-out reader stands nowhere")
            .isEqualTo("none")

        assertThat(json(getJson("/api/v1/users/$subjectId", reader.headers))["state"].asText()).isEqualTo("none")

        val connectionId = json(
            sendJson(HttpMethod.POST, "/api/v1/connections", mapOf("userId" to subjectId), reader.headers),
        )["id"].asText()
        val asked = json(getJson("/api/v1/users/$subjectId", reader.headers))
        assertThat(asked["state"].asText()).isEqualTo("outgoing")
        assertThat(json(getJson("/api/v1/users/${idOf(reader.headers)}", subject.headers))["state"].asText())
            .describedAs("the other side sees the question coming")
            .isEqualTo("incoming")

        sendJson(HttpMethod.POST, "/api/v1/connections/$connectionId/accept", null, subject.headers)
        assertThat(json(getJson("/api/v1/users/$subjectId", reader.headers))["state"].asText())
            .isEqualTo("connected")

        assertThat(json(getJson("/api/v1/users/${idOf(reader.headers)}", reader.headers))["state"].asText())
            .describedAs("you stand nowhere with yourself")
            .isEqualTo("none")
    }

    @Test
    fun `a card for somebody who is not there is a 404`() {
        val reader = registerUser("Card Misser")

        val res = getJson("/api/v1/users/${java.util.UUID.randomUUID()}", reader.headers)

        assertThat(res.statusCode.value()).isEqualTo(404)
    }

    @Test
    fun `a thread can be opened with a connection, and nobody else`() {
        val one = registerUser("Chat One")
        val other = registerUser("Chat Other")
        val stranger = registerUser("Chat Stranger")

        val refused = sendJson(
            HttpMethod.POST, "/api/v1/conversations/with/${idOf(stranger.headers)}", null, one.headers,
        )
        assertThat(refused.statusCode.value()).isEqualTo(403)
        assertThat(json(refused)["code"].asText()).isEqualTo("not_connected")

        val id = json(
            sendJson(
                HttpMethod.POST, "/api/v1/connections",
                mapOf("userId" to idOf(other.headers)), one.headers,
            ),
        )["id"].asText()
        sendJson(HttpMethod.POST, "/api/v1/connections/$id/accept", null, other.headers)

        val opened = sendJson(
            HttpMethod.POST, "/api/v1/conversations/with/${idOf(other.headers)}", null, one.headers,
        )
        assertThat(opened.statusCode.value()).isEqualTo(200)
        val conversationId = json(opened)["id"].asText()

        val again = sendJson(
            HttpMethod.POST, "/api/v1/conversations/with/${idOf(one.headers)}", null, other.headers,
        )
        assertThat(json(again)["id"].asText())
            .describedAs("the same dialogue, from either side")
            .isEqualTo(conversationId)
    }
}
