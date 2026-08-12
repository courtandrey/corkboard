package app.corkboard.features

import app.corkboard.ApiTestBase
import app.corkboard.TestUser
import app.corkboard.auth.Roles
import app.corkboard.jooq.tables.references.FEATURE_FLAGS
import app.corkboard.jooq.tables.references.ROLES
import app.corkboard.jooq.tables.references.USER_ROLES
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.jooq.impl.DSL
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod

class FeatureFlagTest : ApiTestBase() {

    @Autowired
    lateinit var flags: FeatureFlagService

    private val key = FeatureFlag.ARE_USER_DETAILS_EDITABLE.name

    @AfterEach
    fun putItBack() {
        dsl.update(FEATURE_FLAGS).set(FEATURE_FLAGS.ENABLED, true).where(FEATURE_FLAGS.KEY.eq(key)).execute()
        flags.refresh()
    }

    private fun grant(userId: UUID, roleKey: String) {
        val roleId = dsl.select(ROLES.ID).from(ROLES).where(ROLES.KEY.eq(roleKey)).fetchOne(ROLES.ID)!!
        dsl.insertInto(USER_ROLES)
            .set(USER_ROLES.USER_ID, userId)
            .set(USER_ROLES.ROLE_ID, roleId)
            .onConflictDoNothing()
            .execute()
    }

    private fun rename(user: TestUser, name: String) =
        sendJson(HttpMethod.PATCH, "/api/v1/auth/me", mapOf("displayName" to name), user.headers)

    @Test
    fun `everyone can read which features are on`() {
        val res = getJson("/api/v1/features")

        assertThat(res.statusCode.value()).isEqualTo(200)
        assertThat(json(res)["flags"][key].asBoolean()).isTrue()
    }

    @Test
    fun `switching the toggle off closes the endpoint it guards`() {
        val admin = registerUser("Flag Admin").also { grant(it.id, Roles.ADMIN) }
        val resident = registerUser("Renamer")

        assertThat(rename(resident, "Renamed Once").statusCode.value()).isEqualTo(200)

        val off = sendJson(
            HttpMethod.PATCH, "/api/v1/admin/features/$key",
            mapOf("enabled" to false), admin.headers,
        )
        assertThat(off.statusCode.value()).isEqualTo(200)
        assertThat(json(off)["enabled"].asBoolean()).isFalse()
        assertThat(json(off)["updatedBy"].asText()).isEqualTo("Flag Admin")

        val blocked = rename(resident, "Renamed Twice")
        assertThat(blocked.statusCode.value()).isEqualTo(403)
        assertThat(json(blocked)["code"].asText()).isEqualTo("feature_disabled")
        assertThat(json(getJson("/api/v1/features"))["flags"][key].asBoolean()).isFalse()

        sendJson(HttpMethod.PATCH, "/api/v1/admin/features/$key", mapOf("enabled" to true), admin.headers)
        assertThat(rename(resident, "Renamed Twice").statusCode.value()).isEqualTo(200)
    }

    @Test
    fun `a moderator without the permission cannot flip anything`() {
        val moderator = registerUser("Nosy Moderator").also { grant(it.id, Roles.MODERATOR) }

        val listed = getJson("/api/v1/admin/features", moderator.headers)
        val flipped = sendJson(
            HttpMethod.PATCH, "/api/v1/admin/features/$key",
            mapOf("enabled" to false), moderator.headers,
        )

        assertThat(listed.statusCode.value()).isEqualTo(403)
        assertThat(flipped.statusCode.value()).isEqualTo(403)
        assertThat(flags.isEnabled(FeatureFlag.ARE_USER_DETAILS_EDITABLE)).isTrue()
    }

    @Test
    fun `an unknown key is not a flag`() {
        val admin = registerUser("Flag Admin").also { grant(it.id, Roles.ADMIN) }

        val res = sendJson(
            HttpMethod.PATCH, "/api/v1/admin/features/NO_SUCH_FLAG",
            mapOf("enabled" to false), admin.headers,
        )

        assertThat(res.statusCode.value()).isEqualTo(404)
    }

    @Test
    fun `a change made by another instance reaches this cache over the bus`() {
        dsl.update(FEATURE_FLAGS).set(FEATURE_FLAGS.ENABLED, false).where(FEATURE_FLAGS.KEY.eq(key)).execute()
        dsl.select(
            DSL.function(
                "pg_notify",
                String::class.java,
                DSL.`val`(FeatureFlagBus.CHANNEL),
                DSL.`val`(key),
            ),
        ).execute()

        val deadline = System.nanoTime() + 10_000_000_000L
        while (flags.isEnabled(FeatureFlag.ARE_USER_DETAILS_EDITABLE) && System.nanoTime() < deadline) {
            Thread.sleep(50)
        }

        assertThat(flags.isEnabled(FeatureFlag.ARE_USER_DETAILS_EDITABLE))
            .describedAs("the LISTEN/NOTIFY bus should have invalidated the cached snapshot")
            .isFalse()
    }
}
