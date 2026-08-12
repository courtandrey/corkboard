package app.corkboard.auth

import app.corkboard.ApiTestBase
import app.corkboard.TestUser
import app.corkboard.jooq.tables.references.ROLES
import app.corkboard.jooq.tables.references.USER_ROLES
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod

class Spec12RolesTest : ApiTestBase() {

    private fun grantRole(userId: UUID, roleKey: String) {
        val roleId = dsl.select(ROLES.ID).from(ROLES).where(ROLES.KEY.eq(roleKey)).fetchOne(ROLES.ID)!!
        dsl.insertInto(USER_ROLES)
            .set(USER_ROLES.USER_ID, userId)
            .set(USER_ROLES.ROLE_ID, roleId)
            .onConflictDoNothing()
            .execute()
    }

    private fun permissionsOf(user: TestUser): List<String> =
        json(getJson("/api/v1/auth/me", user.headers))["user"]["permissions"].map { it.asText() }

    @Test
    fun `confirming an address is a set of permissions, not a status the endpoints read`() {
        val fresh = registerUnverifiedUser("Roleless Resident")

        assertThat(json(getJson("/api/v1/auth/me", fresh.headers))["user"]["roles"].map { it.asText() })
            .describedAs("everyone signed in is a resident; nothing more until they confirm")
            .containsExactly("resident")
        assertThat(permissionsOf(fresh)).containsExactly("EVENT_HIDE")

        markEmailVerified(fresh.id)

        assertThat(json(getJson("/api/v1/auth/me", fresh.headers))["user"]["roles"].map { it.asText() })
            .describedAs("the verified role follows the column, so the two can never disagree")
            .containsExactlyInAnyOrder("resident", "verified_resident")
        assertThat(permissionsOf(fresh))
            .contains("EVENT_CREATE", "EVENT_VOTE", "EVENT_REPORT", "EVENT_APPLY", "MESSAGE_SEND")
    }

    @Test
    fun `a granted role adds its permissions and revoking takes them away`() {
        val admin = registerUser("Role Admin")
        grantRole(admin.id, Roles.ADMIN)
        val target = registerUser("Role Target")

        assertThat(permissionsOf(admin)).contains("ROLE_MANAGE", "EVENT_TAKE_DOWN_ANY", "REPORT_QUEUE_VIEW")
        assertThat(permissionsOf(target)).doesNotContain("EVENT_TAKE_DOWN_ANY")

        val granted = sendJson(
            HttpMethod.POST, "/api/v1/admin/users/${target.id}/roles",
            mapOf("role" to Roles.MODERATOR), admin.headers,
        )
        assertThat(granted.statusCode.value()).isEqualTo(204)
        assertThat(permissionsOf(target)).contains("EVENT_TAKE_DOWN_ANY", "REPORT_QUEUE_VIEW")
        assertThat(permissionsOf(target)).describedAs("a moderator is not an admin").doesNotContain("ROLE_MANAGE")

        val revoked = rest.exchange(
            "/api/v1/admin/users/${target.id}/roles/${Roles.MODERATOR}", HttpMethod.DELETE,
            org.springframework.http.HttpEntity<Void>(admin.headers), String::class.java,
        )
        assertThat(revoked.statusCode.value()).isEqualTo(204)
        assertThat(permissionsOf(target)).doesNotContain("EVENT_TAKE_DOWN_ANY")
    }

    @Test
    fun `the implicit roles cannot be handed out by hand`() {
        val admin = registerUser("Implicit Admin")
        grantRole(admin.id, Roles.ADMIN)
        val target = registerUser("Implicit Target")

        val refused = sendJson(
            HttpMethod.POST, "/api/v1/admin/users/${target.id}/roles",
            mapOf("role" to Roles.VERIFIED_RESIDENT), admin.headers,
        )
        assertThat(refused.statusCode.value()).isEqualTo(422)
        assertThat(json(refused)["code"].asText()).isEqualTo("role_not_grantable")
    }

    @Test
    fun `role management is closed to residents and moderators alike`() {
        val resident = registerUser("Plain Resident")
        val moderator = registerUser("Plain Moderator")
        grantRole(moderator.id, Roles.MODERATOR)
        val target = registerUser("Someone Else")

        for (actor in listOf(resident, moderator)) {
            val refused = sendJson(
                HttpMethod.POST, "/api/v1/admin/users/${target.id}/roles",
                mapOf("role" to Roles.ADMIN), actor.headers,
            )
            assertThat(refused.statusCode.value()).isEqualTo(403)
            assertThat(json(refused)["code"].asText())
                .describedAs("a confirmed account being refused is a plain no, not a nudge to confirm")
                .isEqualTo("forbidden")
        }
    }
}
