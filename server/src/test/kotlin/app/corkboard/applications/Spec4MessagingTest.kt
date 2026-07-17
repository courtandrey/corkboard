package app.corkboard.applications

import app.corkboard.ApiTestBase
import app.corkboard.TestUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod

class Spec4MessagingTest : ApiTestBase() {

    private fun applyTo(eventId: String, applicant: TestUser, message: String): Pair<String, String> {
        val res = sendJson(
            HttpMethod.POST, "/api/v1/events/$eventId/apply",
            mapOf("message" to message), applicant.headers,
        )
        check(res.statusCode.value() == 201) { res.body ?: "" }
        return json(res)["application"]["id"].asText() to json(res)["conversationId"].asText()
    }

    @Test
    fun `accept notifies the applicant and the thread carries messages both ways with unread counts`() {
        val author = registerUser("Msg Author")
        val applicant = registerUser("Msg Applicant")
        val eventId = createEvent(author, 35.3, 65.1, title = "Bookshelf pickup")
        val (applicationId, conversationId) = applyTo(eventId, applicant, "Can pick it up tonight.")

        val accepted = sendJson(
            HttpMethod.PATCH, "/api/v1/applications/$applicationId",
            mapOf("status" to "accepted"), author.headers,
        )
        assertThat(accepted.statusCode.value()).isEqualTo(200)
        assertThat(json(accepted)["status"].asText()).isEqualTo("accepted")

        val applicantNotifications = json(getJson("/api/v1/notifications", applicant.headers))
        val statusNote = applicantNotifications["items"].first { it["kind"].asText() == "application_status" }
        assertThat(statusNote["payload"]["status"].asText()).isEqualTo("accepted")
        assertThat(statusNote["payload"]["eventId"].asText()).isEqualTo(eventId)

        val reply = sendJson(
            HttpMethod.POST, "/api/v1/conversations/$conversationId/messages",
            mapOf("body" to "Great — ring the top bell."), author.headers,
        )
        assertThat(reply.statusCode.value()).isEqualTo(201)
        sendJson(
            HttpMethod.POST, "/api/v1/conversations/$conversationId/messages",
            mapOf("body" to "On my way!"), applicant.headers,
        )

        val thread = json(getJson("/api/v1/conversations/$conversationId/messages", applicant.headers))
        assertThat(thread["items"].map { it["body"].asText() }).containsExactly(
            "Can pick it up tonight.", "Great — ring the top bell.", "On my way!",
        )

        val authorList = json(getJson("/api/v1/conversations", author.headers))
        val authorSummary = authorList["items"].first { it["id"].asText() == conversationId }
        assertThat(authorSummary["unreadCount"].asInt()).isEqualTo(2)
        assertThat(authorSummary["applicationStatus"].asText()).isEqualTo("accepted")

        val applicantSummary = json(getJson("/api/v1/conversations", applicant.headers))["items"]
            .first { it["id"].asText() == conversationId }
        assertThat(applicantSummary["unreadCount"].asInt()).isEqualTo(1)

        assertThat(
            sendJson(HttpMethod.POST, "/api/v1/conversations/$conversationId/read", null, author.headers)
                .statusCode.value()
        ).isEqualTo(204)
        val afterRead = json(getJson("/api/v1/conversations", author.headers))["items"]
            .first { it["id"].asText() == conversationId }
        assertThat(afterRead["unreadCount"].asInt()).isEqualTo(0)

        val applicantThread = json(getJson("/api/v1/conversations/$conversationId/messages", applicant.headers))
        val mine = applicantThread["items"].filter { it["body"].asText() != "Great — ring the top bell." }
        assertThat(mine).allSatisfy { assertThat(it["readAt"].isNull).isFalse }
    }

    @Test
    fun `withdrawn applications cannot be accepted and strangers see nothing`() {
        val author = registerUser()
        val applicant = registerUser()
        val stranger = registerUser()
        val eventId = createEvent(author, 35.5, 65.3)
        val (applicationId, conversationId) = applyTo(eventId, applicant, "Interested!")

        assertThat(
            getJson("/api/v1/conversations/$conversationId/messages", stranger.headers).statusCode.value()
        ).isEqualTo(404)
        assertThat(
            sendJson(
                HttpMethod.PATCH, "/api/v1/applications/$applicationId",
                mapOf("status" to "accepted"), stranger.headers,
            ).statusCode.value()
        ).isEqualTo(404)
        assertThat(
            sendJson(
                HttpMethod.PATCH, "/api/v1/applications/$applicationId",
                mapOf("status" to "accepted"), applicant.headers,
            ).statusCode.value()
        ).isEqualTo(403)

        val withdrawn = sendJson(
            HttpMethod.POST, "/api/v1/applications/$applicationId/withdraw", null, applicant.headers,
        )
        assertThat(withdrawn.statusCode.value()).isEqualTo(200)
        assertThat(json(withdrawn)["status"].asText()).isEqualTo("withdrawn")

        val lateAccept = sendJson(
            HttpMethod.PATCH, "/api/v1/applications/$applicationId",
            mapOf("status" to "accepted"), author.headers,
        )
        assertThat(lateAccept.statusCode.value()).isEqualTo(409)
        assertThat(json(lateAccept)["code"].asText()).isEqualTo("invalid_status")

        val stillReadable = getJson("/api/v1/conversations/$conversationId/messages", applicant.headers)
        assertThat(stillReadable.statusCode.value()).isEqualTo(200)
    }

    @Test
    fun `me-applications lists sent and received grouped by event`() {
        val author = registerUser("Grouped Author")
        val first = registerUser("First Applicant")
        val second = registerUser("Second Applicant")
        val eventId = createEvent(author, 35.7, 65.5, title = "Two responders wanted")
        applyTo(eventId, first, "First here.")
        applyTo(eventId, second, "Second here.")

        val received = json(getJson("/api/v1/me/applications?role=received", author.headers))
        val group = received["items"].first { it["event"]["id"].asText() == eventId }
        assertThat(group["applications"]).hasSize(2)
        assertThat(group["applications"].map { it["applicant"]["displayName"].asText() })
            .containsExactlyInAnyOrder("First Applicant", "Second Applicant")
        assertThat(group["applications"].map { it["message"].asText() })
            .containsExactlyInAnyOrder("First here.", "Second here.")

        val sent = json(getJson("/api/v1/me/applications?role=sent", first.headers))
        val sentGroup = sent["items"].first { it["event"]["id"].asText() == eventId }
        assertThat(sentGroup["applications"]).hasSize(1)
        val applicantNode = sentGroup["applications"][0].path("applicant")
        assertThat(applicantNode.isMissingNode || applicantNode.isNull).isTrue
    }
}
