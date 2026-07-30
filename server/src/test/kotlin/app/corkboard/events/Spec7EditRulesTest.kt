package app.corkboard.events

import app.corkboard.ApiTestBase
import app.corkboard.jooq.tables.references.APPLICATIONS
import app.corkboard.jooq.tables.references.EVENTS
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod

class Spec7EditRulesTest : ApiTestBase() {


    private fun createEvent(user: app.corkboard.TestUser): String {
        val res = sendJson(
            HttpMethod.POST, "/api/v1/events",
            mapOf(
                "type" to "activity",
                "title" to "Five-a-side football, Sundays",
                "body" to "Casual game at the East River fields, all levels welcome.",
                "location" to mapOf("lng" to -73.975, "lat" to 40.715),
                "applyable" to true,
                "expiresAt" to Instant.now().plus(30, ChronoUnit.DAYS).toString(),
            ),
            user.headers,
        )
        check(res.statusCode.value() == 201) { res.body ?: "" }
        return json(res)["id"].asText()
    }

    @Test
    fun `author edits stay open but type and location lock after the first application`() {
        val author = registerUser("Author")
        val applicant = registerUser("Applicant")
        val id = createEvent(author)

        val retitled = sendJson(
            HttpMethod.PATCH, "/api/v1/events/$id",
            mapOf("title" to "Five-a-side football, Saturdays"), author.headers,
        )
        assertThat(retitled.statusCode.value()).isEqualTo(200)
        assertThat(json(retitled)["title"].asText()).isEqualTo("Five-a-side football, Saturdays")

        val moved = sendJson(
            HttpMethod.PATCH, "/api/v1/events/$id",
            mapOf("location" to mapOf("lng" to -73.96, "lat" to 40.72)), author.headers,
        )
        assertThat(moved.statusCode.value()).isEqualTo(200)

        dsl.insertInto(APPLICATIONS)
            .set(APPLICATIONS.EVENT_ID, UUID.fromString(id))
            .set(APPLICATIONS.APPLICANT_ID, applicant.id)
            .execute()
        val count = dsl.select(EVENTS.APPLICATION_COUNT).from(EVENTS)
            .where(EVENTS.ID.eq(UUID.fromString(id))).fetchOne(EVENTS.APPLICATION_COUNT)
        assertThat(count).isEqualTo(1)

        val lockedLocation = sendJson(
            HttpMethod.PATCH, "/api/v1/events/$id",
            mapOf("location" to mapOf("lng" to -73.95, "lat" to 40.73)), author.headers,
        )
        assertThat(lockedLocation.statusCode.value()).isEqualTo(409)
        assertThat(json(lockedLocation)["code"].asText()).isEqualTo("edit_locked")

        val lockedType = sendJson(
            HttpMethod.PATCH, "/api/v1/events/$id",
            mapOf("type" to "club"), author.headers,
        )
        assertThat(lockedType.statusCode.value()).isEqualTo(409)

        val stillEditable = sendJson(
            HttpMethod.PATCH, "/api/v1/events/$id",
            mapOf("body" to "Moved to Saturdays — same fields, same friendly level.", "tags" to listOf("5-a-side")),
            author.headers,
        )
        assertThat(stillEditable.statusCode.value()).isEqualTo(200)
        assertThat(json(stillEditable)["tags"].map { it["slug"].asText() }).containsExactly("5-a-side")
    }

    @Test
    fun `only the author may edit`() {
        val author = registerUser()
        val stranger = registerUser()
        val id = createEvent(author)

        val res = sendJson(
            HttpMethod.PATCH, "/api/v1/events/$id",
            mapOf("title" to "Hijacked title"), stranger.headers,
        )
        assertThat(res.statusCode.value()).isEqualTo(403)
        assertThat(json(res)["code"].asText()).isEqualTo("forbidden")
    }

    @Test
    fun `removed notes 404 for strangers but stay readable for the author`() {
        val author = registerUser()
        val stranger = registerUser()
        val id = createEvent(author)

        dsl.update(EVENTS)
            .set(EVENTS.STATUS, app.corkboard.jooq.enums.EventStatus.removed)
            .where(EVENTS.ID.eq(UUID.fromString(id)))
            .execute()

        assertThat(getJson("/api/v1/events/$id", stranger.headers).statusCode.value()).isEqualTo(404)
        assertThat(getJson("/api/v1/events/$id").statusCode.value()).isEqualTo(404)
        assertThat(getJson("/api/v1/events/$id", author.headers).statusCode.value()).isEqualTo(200)
    }
}
