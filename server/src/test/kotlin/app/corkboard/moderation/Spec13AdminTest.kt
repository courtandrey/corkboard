package app.corkboard.moderation

import app.corkboard.ApiTestBase
import app.corkboard.auth.Roles
import app.corkboard.jooq.tables.references.ROLES
import app.corkboard.jooq.tables.references.USER_ROLES
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod

class Spec13AdminTest : ApiTestBase() {

    private fun makeModerator(userId: UUID) {
        val roleId = dsl.select(ROLES.ID).from(ROLES).where(ROLES.KEY.eq(Roles.MODERATOR)).fetchOne(ROLES.ID)!!
        dsl.insertInto(USER_ROLES)
            .set(USER_ROLES.USER_ID, userId)
            .set(USER_ROLES.ROLE_ID, roleId)
            .onConflictDoNothing()
            .execute()
    }

    private fun report(eventId: String, reporterName: String, reason: String) {
        reportAs(registerUser(reporterName), eventId, reason)
    }

    private fun reportAs(reporter: app.corkboard.TestUser, eventId: String, reason: String) {
        val res = sendJson(
            HttpMethod.POST, "/api/v1/events/$eventId/report",
            mapOf("reason" to reason), reporter.headers,
        )
        check(res.statusCode.value() == 202) { "report failed: ${res.body}" }
    }

    private fun queueIds(moderator: app.corkboard.TestUser): List<String> =
        json(getJson("/api/v1/admin/reports", moderator.headers))["items"].map { it["id"].asText() }

    @Test
    fun `the queue puts the worst-reported note first and breaks its reasons down`() {
        val author = registerUser("Reported Author")
        val quiet = createEvent(author, 21.1, 41.1, title = "Mildly disliked")
        val loud = createEvent(author, 21.2, 41.2, title = "Widely reported")

        report(quiet, "Quiet Reporter", "spam")
        report(loud, "Loud Reporter A", "spam")
        report(loud, "Loud Reporter B", "spam")
        report(loud, "Loud Reporter C", "scam")

        val moderator = registerUser("Queue Moderator")
        makeModerator(moderator.id)

        val queue = json(getJson("/api/v1/admin/reports", moderator.headers))["items"]
        val ids = queue.map { it["id"].asText() }
        assertThat(ids.indexOf(loud))
            .describedAs("three reports outrank one")
            .isLessThan(ids.indexOf(quiet))

        val worst = queue.first { it["id"].asText() == loud }
        assertThat(worst["reportCount"].asInt()).isEqualTo(3)
        assertThat(worst["title"].asText()).isEqualTo("Widely reported")
        assertThat(worst["authorDisplayName"].asText()).isEqualTo("Reported Author")
        assertThat(worst["reasons"].map { "${it["reason"].asText()}:${it["count"].asInt()}" })
            .containsExactly("spam:2", "scam:1")
    }

    @Test
    fun `a moderator can take down anyone's note, and put an auto-hidden one back`() {
        val author = registerUser("Unlucky Author")
        val eventId = createEvent(author, 22.1, 42.1, title = "Someone else's note")
        val moderator = registerUser("Acting Moderator")
        makeModerator(moderator.id)

        assertThat(sendJson(HttpMethod.POST, "/api/v1/admin/events/$eventId/takedown", null, moderator.headers)
            .statusCode.value()).isEqualTo(204)
        assertThat(json(getJson("/api/v1/events/$eventId", author.headers))["status"].asText())
            .describedAs("its own status: the author did not withdraw this one")
            .isEqualTo("taken_down")

        val told = json(getJson("/api/v1/notifications", author.headers))["items"]
        val alert = told.first { it["payload"]["eventId"].asText() == eventId }
        assertThat(alert["kind"].asText()).isEqualTo("event_taken_down")
        assertThat(alert["payload"]["eventTitle"].asText()).isEqualTo("Someone else's note")

        assertThat(sendJson(HttpMethod.POST, "/api/v1/admin/events/$eventId/restore", null, moderator.headers)
            .statusCode.value()).isEqualTo(204)
        assertThat(json(getJson("/api/v1/events/$eventId", author.headers))["status"].asText()).isEqualTo("active")
    }

    @Test
    fun `approving clears the reports, puts an auto-hidden note back, and empties the row from the queue`() {
        val author = registerUser("Cleared Author")
        val eventId = createEvent(author, 24.1, 44.1, title = "Reported but fine")
        val moderator = registerUser("Approving Moderator")
        makeModerator(moderator.id)

        val reporter = registerUser("Second-Guessing Reporter")
        reportAs(reporter, eventId, "spam")
        repeat(4) { report(eventId, "Piling Reporter $it", "spam") }
        assertThat(json(getJson("/api/v1/events/$eventId", author.headers))["status"].asText())
            .describedAs("five reports auto-hide it")
            .isEqualTo("under_review")

        assertThat(sendJson(HttpMethod.POST, "/api/v1/admin/events/$eventId/approve", null, moderator.headers)
            .statusCode.value()).isEqualTo(204)

        assertThat(json(getJson("/api/v1/events/$eventId", author.headers))["status"].asText())
            .describedAs("clearing the reports has to undo the auto-hide they caused")
            .isEqualTo("active")
        assertThat(queueIds(moderator))
            .describedAs("nothing is left to decide, so it leaves the queue")
            .doesNotContain(eventId)

        val again = sendJson(
            HttpMethod.POST, "/api/v1/events/$eventId/report",
            mapOf("reason" to "spam"), reporter.headers,
        )
        assertThat(again.statusCode.value()).isEqualTo(202)
        assertThat(queueIds(moderator))
            .describedAs("a reviewed report is still that reporter's one and only")
            .doesNotContain(eventId)
    }

    @Test
    fun `a note that was taken down is off the queue as well as off the board`() {
        val author = registerUser("Removed Author")
        val eventId = createEvent(author, 25.1, 45.1, title = "Gone for good")
        val moderator = registerUser("Deciding Moderator")
        makeModerator(moderator.id)

        report(eventId, "Sole Reporter", "scam")
        assertThat(queueIds(moderator)).contains(eventId)

        sendJson(HttpMethod.POST, "/api/v1/admin/events/$eventId/takedown", null, moderator.headers)

        assertThat(queueIds(moderator)).doesNotContain(eventId)

        sendJson(HttpMethod.POST, "/api/v1/admin/events/$eventId/restore", null, moderator.headers)
        assertThat(queueIds(moderator))
            .describedAs("and putting it back does not reopen a decision already made")
            .doesNotContain(eventId)
    }

    @Test
    fun `an ordinary resident can neither read the queue nor take anything down`() {
        val author = registerUser("Bystander Author")
        val eventId = createEvent(author, 23.1, 43.1, title = "Not yours to remove")
        val resident = registerUser("Nosy Resident")

        assertThat(getJson("/api/v1/admin/reports", resident.headers).statusCode.value()).isEqualTo(403)

        val refused = sendJson(HttpMethod.POST, "/api/v1/admin/events/$eventId/takedown", null, resident.headers)
        assertThat(refused.statusCode.value()).isEqualTo(403)
        assertThat(json(getJson("/api/v1/events/$eventId", author.headers))["status"].asText())
            .describedAs("the note is untouched")
            .isEqualTo("active")
    }
}
