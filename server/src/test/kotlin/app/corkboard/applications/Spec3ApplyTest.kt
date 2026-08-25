package app.corkboard.applications

import app.corkboard.ApiTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod

class Spec3ApplyTest : ApiTestBase() {

    @Test
    fun `apply creates application conversation first message and notification atomically`() {
        val author = registerUser("Apply Author")
        val applicant = registerUser("Apply Applicant")
        val eventId = createEvent(author, 34.3, 64.1, title = "Cat sitter wanted")

        val applied = sendJson(
            HttpMethod.POST, "/api/v1/events/$eventId/apply",
            mapOf("message" to "I love cats and live around the corner."),
            applicant.headers,
        )
        assertThat(applied.statusCode.value()).isEqualTo(201)
        val conversationId = json(applied)["conversationId"].asText()
        assertThat(json(applied)["application"]["status"].asText()).isEqualTo("pending")
        assertThat(json(applied)["application"]["eventId"].asText()).isEqualTo(eventId)

        val conversations = json(getJson("/api/v1/conversations", applicant.headers))
        val summary = conversations["items"].first { it["id"].asText() == conversationId }
        assertThat(summary["otherParty"]["displayName"].asText()).isEqualTo("Apply Author")
        assertThat(summary["lastMessageBody"].asText()).isEqualTo("I love cats and live around the corner.")
        assertThat(summary["unreadCount"].asInt()).isEqualTo(0)

        val messages = json(getJson("/api/v1/conversations/$conversationId/messages", author.headers))
        assertThat(messages["items"]).hasSize(1)
        assertThat(messages["items"][0]["body"].asText()).isEqualTo("I love cats and live around the corner.")
        val answered = messages["items"][0]["event"]
        assertThat(answered["id"].asText()).describedAs("the note answered rides on the message").isEqualTo(eventId)
        assertThat(answered["title"].asText()).isEqualTo("Cat sitter wanted")

        val detail = json(getJson("/api/v1/events/$eventId", applicant.headers))
        assertThat(detail["viewerState"]["applied"].asBoolean()).isTrue
        assertThat(detail["applicationCount"].asInt()).isEqualTo(1)

        val notifications = json(getJson("/api/v1/notifications", author.headers))
        assertThat(notifications["unreadCount"].asInt()).isGreaterThanOrEqualTo(1)
        val received = notifications["items"].first { it["kind"].asText() == "application_received" }
        assertThat(received["payload"]["eventId"].asText()).isEqualTo(eventId)
        assertThat(received["payload"]["conversationId"].asText()).isEqualTo(conversationId)

        val duplicate = sendJson(
            HttpMethod.POST, "/api/v1/events/$eventId/apply",
            mapOf("message" to "Me again."), applicant.headers,
        )
        assertThat(duplicate.statusCode.value()).isEqualTo(409)
        assertThat(json(duplicate)["code"].asText()).isEqualTo("already_applied")
    }

    @Test
    fun `answering a second note continues the same conversation`() {
        val author = registerUser("Two Notes Author")
        val applicant = registerUser("Two Notes Applicant")
        val first = createEvent(author, 34.31, 64.11, title = "Cat sitter wanted")
        val second = createEvent(author, 34.32, 64.12, title = "Ladder to borrow")

        fun applyTo(eventId: String, message: String) = json(
            sendJson(
                HttpMethod.POST, "/api/v1/events/$eventId/apply",
                mapOf("message" to message), applicant.headers,
            )
        )["conversationId"].asText()

        val opened = applyTo(first, "I love cats.")
        val again = applyTo(second, "I have a ladder.")
        assertThat(again).describedAs("one dialogue per pair, whatever the note").isEqualTo(opened)

        val conversations = json(getJson("/api/v1/conversations", applicant.headers))
        assertThat(conversations["items"].count { it["otherParty"]["displayName"].asText() == "Two Notes Author" })
            .isEqualTo(1)

        val messages = json(getJson("/api/v1/conversations/$opened/messages", author.headers))["items"]
        assertThat(messages).hasSize(2)
        assertThat(messages.map { it["event"]["id"].asText() }).containsExactly(first, second)
        assertThat(messages.map { it["event"]["title"].asText() })
            .containsExactly("Cat sitter wanted", "Ladder to borrow")

        val plain = json(
            sendJson(
                HttpMethod.POST, "/api/v1/conversations/$opened/messages",
                mapOf("body" to "Tomorrow at six?"), author.headers,
            )
        )
        assertThat(plain["event"].isNull).describedAs("an ordinary message answers no note").isTrue
    }

    @Test
    fun `apply rejects own notes and non-applyable notes`() {
        val author = registerUser()
        val other = registerUser()
        val applyable = createEvent(author, 34.5, 64.3)
        val notApplyable = createEvent(author, 34.6, 64.4, type = "notice", applyable = false)

        val own = sendJson(
            HttpMethod.POST, "/api/v1/events/$applyable/apply",
            mapOf("message" to "Applying to myself."), author.headers,
        )
        assertThat(own.statusCode.value()).isEqualTo(409)
        assertThat(json(own)["code"].asText()).isEqualTo("own_event")

        val closed = sendJson(
            HttpMethod.POST, "/api/v1/events/$notApplyable/apply",
            mapOf("message" to "Let me in anyway."), other.headers,
        )
        assertThat(closed.statusCode.value()).isEqualTo(409)
        assertThat(json(closed)["code"].asText()).isEqualTo("not_applyable")
    }

    @Test
    fun `reading notifications takes them off the board`() {
        val author = registerUser()
        val applicant = registerUser()
        val eventId = createEvent(author, 34.7, 64.5)
        sendJson(
            HttpMethod.POST, "/api/v1/events/$eventId/apply",
            mapOf("message" to "Count me in."), applicant.headers,
        )

        assertThat(json(getJson("/api/v1/notifications", author.headers))["unreadCount"].asInt()).isEqualTo(1)
        sendJson(HttpMethod.POST, "/api/v1/notifications/read", null, author.headers)
        val after = json(getJson("/api/v1/notifications", author.headers))
        assertThat(after["unreadCount"].asInt()).isEqualTo(0)
        assertThat(after["items"]).isEmpty()
    }
}
