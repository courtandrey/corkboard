package app.corkboard.scopes

import app.corkboard.ApiTestBase
import app.corkboard.TestUser
import app.corkboard.auth.Roles
import app.corkboard.features.FeatureFlag
import app.corkboard.features.FeatureFlagService
import app.corkboard.jooq.tables.references.FEATURE_FLAGS
import app.corkboard.jooq.tables.references.ROLES
import app.corkboard.jooq.tables.references.USER_ROLES
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity

class PersonalScopeTest : ApiTestBase() {

    @Autowired
    lateinit var flags: FeatureFlagService

    private val bbox = "30.10,50.10,30.30,50.30"

    @AfterEach
    fun leaveItOn() {
        dsl.update(FEATURE_FLAGS)
            .set(FEATURE_FLAGS.ENABLED, true)
            .where(FEATURE_FLAGS.KEY.eq(FeatureFlag.IS_PERSONAL_SCOPE_ENABLED.name))
            .execute()
        flags.refresh()
    }

    private fun switchOff(actor: UUID) {
        flags.set(FeatureFlag.IS_PERSONAL_SCOPE_ENABLED, false, actor)
    }

    private fun board(owner: UUID) = "/api/v1/boards/$owner/events"

    private fun view(user: TestUser?, path: String) =
        getJson("$path?bbox=$bbox&zoom=13&clustered=false", user?.headers ?: HttpHeaders())

    private fun pin(
        user: TestUser,
        path: String,
        type: String,
        title: String,
    ): ResponseEntity<String> = sendJson(
        HttpMethod.POST, path,
        mapOf(
            "type" to type,
            "title" to title,
            "body" to "Written by the regression suite.",
            "location" to mapOf("lng" to 30.2, "lat" to 50.2),
            "applyable" to true,
        ),
        user.headers,
    )

    @Test
    fun `a board is addressed by its owner, and holds only what was pinned to it`() {
        val owner = registerUser("Board Owner")

        val created = pin(owner, board(owner.id), "memory", "The bench in the rain")
        assertThat(created.statusCode.value()).isEqualTo(201)
        val id = json(created)["id"].asText()
        assertThat(json(created)["scope"].asText()).isEqualTo("personal")
        assertThat(json(created)["boardOwnerId"].asText())
            .describedAs("the note says which board it is on, so a client can address it again")
            .isEqualTo(owner.id.toString())

        assertThat(json(view(owner, board(owner.id)))["items"].map { it["id"].asText() }).contains(id)
        assertThat(getJson("${board(owner.id)}/$id", owner.headers).statusCode.value()).isEqualTo(200)

        assertThat(json(view(owner, "/api/v1/events"))["items"].map { it["id"].asText() })
            .describedAs("the shared board never carries a personal note")
            .doesNotContain(id)
        assertThat(getJson("/api/v1/events/$id", owner.headers).statusCode.value())
            .describedAs("nor does its addressing reach one, even for the owner")
            .isEqualTo(404)
    }

    @Test
    fun `somebody else's board is closed, and its notes are not found`() {
        val owner = registerUser("Private Author")
        val stranger = registerUser("Curious Stranger")
        val id = json(pin(owner, board(owner.id), "notice", "Just for me"))["id"].asText()

        val theirBoard = view(stranger, board(owner.id))
        val theirNote = getJson("${board(owner.id)}/$id", stranger.headers)

        assertThat(theirBoard.statusCode.value()).isEqualTo(403)
        assertThat(json(theirBoard)["code"].asText()).isEqualTo("scope_forbidden")
        assertThat(theirNote.statusCode.value()).isEqualTo(403)
        assertThat(getJson("${board(stranger.id)}/$id", stranger.headers).statusCode.value())
            .describedAs("and it is not on their own board either")
            .isEqualTo(404)
    }

    @Test
    fun `a board nobody signed in for is not readable`() {
        val owner = registerUser("Signed-out Subject")

        assertThat(view(null, board(owner.id)).statusCode.value()).isEqualTo(401)
        assertThat(view(null, board(UUID.randomUUID())).statusCode.value()).isEqualTo(401)
    }

    @Test
    fun `each board only takes the kinds of note that belong on it`() {
        val resident = registerUser("Taxonomy Resident")

        val wrongOnPersonal = pin(resident, board(resident.id), "help", "Need a hand moving")
        val wrongOnShared = pin(resident, "/api/v1/events", "memory", "That summer")
        val right = pin(resident, board(resident.id), "plan", "Take the ladder back")

        assertThat(wrongOnPersonal.statusCode.value()).isEqualTo(422)
        assertThat(json(wrongOnPersonal)["code"].asText()).isEqualTo("type_not_in_scope")
        assertThat(wrongOnShared.statusCode.value()).isEqualTo(422)
        assertThat(right.statusCode.value()).isEqualTo(201)
    }

    @Test
    fun `a personal note takes no votes, reports, responses or hides`() {
        val owner = registerUser("Inert Owner")
        val id = json(pin(owner, board(owner.id), "notice", "Just for me"))["id"].asText()

        assertThat(json(getJson("${board(owner.id)}/$id", owner.headers))["applyable"].asBoolean())
            .describedAs("nobody can respond, so the flag is not even offered")
            .isFalse()

        val refusals = listOf(
            sendJson(HttpMethod.POST, "/api/v1/events/$id/vote", null, owner.headers),
            sendJson(HttpMethod.POST, "/api/v1/events/$id/report", mapOf("reason" to "spam"), owner.headers),
            sendJson(HttpMethod.POST, "/api/v1/events/$id/apply", mapOf("message" to "Me!"), owner.headers),
            sendJson(HttpMethod.POST, "/api/v1/events/$id/hide", null, owner.headers),
        )

        assertThat(refusals.map { it.statusCode.value() })
            .describedAs("those endpoints belong to the shared board, where this note is not")
            .containsOnly(404)
    }

    @Test
    fun `not even a keeper reaches a personal note`() {
        val owner = registerUser("Guarded Author")
        val id = json(pin(owner, board(owner.id), "memory", "Nobody's business"))["id"].asText()

        val moderator = registerUser("Keen Moderator")
        val roleId = dsl.select(ROLES.ID).from(ROLES).where(ROLES.KEY.eq(Roles.MODERATOR)).fetchOne(ROLES.ID)!!
        dsl.insertInto(USER_ROLES)
            .set(USER_ROLES.USER_ID, moderator.id)
            .set(USER_ROLES.ROLE_ID, roleId)
            .onConflictDoNothing()
            .execute()

        assertThat(getJson("${board(owner.id)}/$id", moderator.headers).statusCode.value()).isEqualTo(403)
        assertThat(getJson("/api/v1/events/$id", moderator.headers).statusCode.value()).isEqualTo(404)
        assertThat(
            sendJson(HttpMethod.POST, "/api/v1/admin/events/$id/takedown", null, moderator.headers)
                .statusCode.value(),
        ).isEqualTo(404)
        assertThat(json(getJson("${board(owner.id)}/$id", owner.headers))["status"].asText()).isEqualTo("active")
    }

    @Test
    fun `the author manages a personal note through its board`() {
        val owner = registerUser("Tidy Owner")
        val id = json(pin(owner, board(owner.id), "plan", "Take the ladder back"))["id"].asText()

        val renamed = sendJson(
            HttpMethod.PATCH, "${board(owner.id)}/$id",
            mapOf("title" to "Take the ladder back to Stan"), owner.headers,
        )
        assertThat(renamed.statusCode.value()).isEqualTo(200)
        assertThat(json(renamed)["title"].asText()).isEqualTo("Take the ladder back to Stan")

        assertThat(
            sendJson(HttpMethod.PATCH, "/api/v1/events/$id", mapOf("title" to "Sneaky"), owner.headers)
                .statusCode.value(),
        ).describedAs("the shared board's addressing does not reach it").isEqualTo(404)

        assertThat(sendJson(HttpMethod.POST, "${board(owner.id)}/$id/resolve", null, owner.headers)
            .statusCode.value()).isEqualTo(200)
        assertThat(sendJson(HttpMethod.DELETE, "${board(owner.id)}/$id", null, owner.headers)
            .statusCode.value()).isEqualTo(204)
    }

    @Test
    fun `with the feature switched off the whole board API answers 403`() {
        val owner = registerUser("Toggled Owner")
        val id = json(pin(owner, board(owner.id), "plan", "Still here tomorrow"))["id"].asText()

        switchOff(owner.id)

        val closed = listOf(
            view(owner, board(owner.id)),
            getJson("${board(owner.id)}/$id", owner.headers),
            pin(owner, board(owner.id), "plan", "One more"),
            sendJson(HttpMethod.PATCH, "${board(owner.id)}/$id", mapOf("title" to "Renamed"), owner.headers),
            sendJson(HttpMethod.DELETE, "${board(owner.id)}/$id", null, owner.headers),
            sendJson(HttpMethod.POST, "${board(owner.id)}/$id/resolve", null, owner.headers),
        )

        assertThat(closed.map { it.statusCode.value() }).containsOnly(403)
        assertThat(closed.map { json(it)["code"].asText() }).containsOnly("feature_disabled")
        assertThat(json(getJson("/api/v1/me/events", owner.headers))["items"].map { it["id"].asText() })
            .describedAs("and a switched-off feature is not listed anywhere else either")
            .doesNotContain(id)

        leaveItOn()
        assertThat(json(view(owner, board(owner.id)))["items"].map { it["id"].asText() })
            .describedAs("the notes were waiting all along")
            .contains(id)
    }
}
