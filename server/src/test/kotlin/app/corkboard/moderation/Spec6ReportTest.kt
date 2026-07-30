package app.corkboard.moderation

import app.corkboard.ApiTestBase
import app.corkboard.jooq.enums.EventStatus
import app.corkboard.jooq.enums.NotificationKind
import app.corkboard.jooq.tables.references.EVENTS
import app.corkboard.jooq.tables.references.NOTIFICATIONS
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod

class Spec6ReportTest : ApiTestBase() {


    @Test
    fun `five distinct reports take the note off the board and notify the author`() {
        val author = registerUser("Reported Author")
        val bbox = "32.20,62.10,32.40,62.20"
        val created = sendJson(
            HttpMethod.POST, "/api/v1/events",
            mapOf(
                "type" to "notice",
                "title" to "Suspicious watch sale",
                "body" to "Genuine Rolexx, best price, meet me behind the dumpster.",
                "location" to mapOf("lng" to 32.3001, "lat" to 62.1501),
                "applyable" to false,
                "expiresAt" to Instant.now().plus(15, ChronoUnit.DAYS).toString(),
            ),
            author.headers,
        )
        val id = json(created)["id"].asText()
        val viewport = "/api/v1/events?bbox=$bbox&zoom=14"

        repeat(4) { i ->
            val reporter = registerUser("Reporter $i")
            val res = sendJson(
                HttpMethod.POST, "/api/v1/events/$id/report",
                mapOf("reason" to "scam"), reporter.headers,
            )
            assertThat(res.statusCode.value()).isEqualTo(202)
        }
        assertThat(json(getJson(viewport))["items"].map { it["id"].asText() }).contains(id)

        val fifth = registerUser("Reporter 4")
        assertThat(
            sendJson(
                HttpMethod.POST, "/api/v1/events/$id/report",
                mapOf("reason" to "scam", "detail" to "Obvious counterfeit pitch."), fifth.headers,
            ).statusCode.value()
        ).isEqualTo(202)

        val status = dsl.select(EVENTS.STATUS).from(EVENTS)
            .where(EVENTS.ID.eq(UUID.fromString(id))).fetchOne(EVENTS.STATUS)
        assertThat(status).isEqualTo(EventStatus.under_review)

        assertThat(json(getJson(viewport))["items"].map { it["id"].asText() }).doesNotContain(id)
        assertThat(getJson("/api/v1/events/$id").statusCode.value()).isEqualTo(404)
        assertThat(getJson("/api/v1/events/$id", author.headers).statusCode.value()).isEqualTo(200)

        val notifications = dsl.fetchCount(
            NOTIFICATIONS,
            NOTIFICATIONS.USER_ID.eq(author.id),
            NOTIFICATIONS.KIND.eq(NotificationKind.event_under_review),
        )
        assertThat(notifications).isEqualTo(1)

        val again = sendJson(
            HttpMethod.POST, "/api/v1/events/$id/report",
            mapOf("reason" to "spam"), fifth.headers,
        )
        assertThat(again.statusCode.value()).isEqualTo(202)
        assertThat(
            dsl.fetchCount(
                NOTIFICATIONS,
                NOTIFICATIONS.USER_ID.eq(author.id),
                NOTIFICATIONS.KIND.eq(NotificationKind.event_under_review),
            )
        ).isEqualTo(1)
    }

    @Test
    fun `duplicate reports are idempotent and own reports rejected`() {
        val author = registerUser()
        val reporter = registerUser()
        val created = sendJson(
            HttpMethod.POST, "/api/v1/events",
            mapOf(
                "type" to "notice",
                "title" to "Perfectly fine note",
                "body" to "Nothing wrong here.",
                "location" to mapOf("lng" to 32.5, "lat" to 62.3),
                "applyable" to false,
                "expiresAt" to Instant.now().plus(15, ChronoUnit.DAYS).toString(),
            ),
            author.headers,
        )
        val id = json(created)["id"].asText()

        repeat(3) {
            assertThat(
                sendJson(
                    HttpMethod.POST, "/api/v1/events/$id/report",
                    mapOf("reason" to "spam"), reporter.headers,
                ).statusCode.value()
            ).isEqualTo(202)
        }
        val count = dsl.select(EVENTS.REPORT_COUNT).from(EVENTS)
            .where(EVENTS.ID.eq(UUID.fromString(id))).fetchOne(EVENTS.REPORT_COUNT)
        assertThat(count).isEqualTo(1)

        val own = sendJson(
            HttpMethod.POST, "/api/v1/events/$id/report",
            mapOf("reason" to "other"), author.headers,
        )
        assertThat(own.statusCode.value()).isEqualTo(409)
        assertThat(json(own)["code"].asText()).isEqualTo("own_event")
    }
}
