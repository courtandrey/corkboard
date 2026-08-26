package app.corkboard.auth

import app.corkboard.ApiTestBase
import app.corkboard.jooq.tables.references.USERS
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class GoogleIdentityServiceTest : ApiTestBase() {

    @Autowired
    lateinit var identity: GoogleIdentityService

    private fun uniqueEmail() = "google-${UUID.randomUUID()}@example.com"

    private fun handle() = "g_${UUID.randomUUID().toString().replace("-", "").take(12)}"

    private fun insertPasswordUser(email: String): UUID =
        dsl.insertInto(USERS)
            .set(USERS.EMAIL, email)
            .set(USERS.HANDLE, handle())
            .set(USERS.DISPLAY_NAME, "Password Person")
            .set(USERS.PASSWORD_HASH, "not-a-real-hash")
            .set(USERS.AVATAR_SEED, UUID.randomUUID().toString())
            .returning(USERS.ID)
            .fetchOne(USERS.ID)!!

    private fun googleSub(id: UUID): String? =
        dsl.select(USERS.GOOGLE_SUB).from(USERS).where(USERS.ID.eq(id)).fetchOne(USERS.GOOGLE_SUB)

    @Test
    fun `matching google_sub signs in without touching the row`() {
        val email = uniqueEmail()
        val sub = "sub-${UUID.randomUUID()}"
        val id = identity.create(GoogleProfile(sub, email, true, "Googler"), handle(), "Googler")

        val again = identity.signIn(GoogleProfile(sub, "changed-$email", true, "Renamed"))

        assertThat((again as GoogleIdentityResult.SignedIn).userId).isEqualTo(id)
        assertThat(dsl.fetchCount(USERS, USERS.GOOGLE_SUB.eq(sub))).isEqualTo(1)
    }

    @Test
    fun `verified email links an existing password account`() {
        val email = uniqueEmail()
        val existing = insertPasswordUser(email)
        val sub = "sub-${UUID.randomUUID()}"

        val result = identity.signIn(GoogleProfile(sub, email, true, "Googler"))

        assertThat((result as GoogleIdentityResult.SignedIn).userId).isEqualTo(existing)
        assertThat(googleSub(existing)).isEqualTo(sub)
    }

    @Test
    fun `unverified email never links an existing account`() {
        val email = uniqueEmail()
        val existing = insertPasswordUser(email)

        val result = identity.signIn(GoogleProfile("sub-${UUID.randomUUID()}", email, false, "Impostor"))

        assertThat(result).isEqualTo(GoogleIdentityResult.EmailConflict)
        assertThat(googleSub(existing)).isNull()
    }

    @Test
    fun `an unknown identity writes nothing until the user id is chosen`() {
        val email = uniqueEmail()

        val result = identity.signIn(GoogleProfile("sub-${UUID.randomUUID()}", email, true, "Newcomer"))

        assertThat(result).isEqualTo(GoogleIdentityResult.NeedsSignup)
        assertThat(dsl.fetchCount(USERS, USERS.EMAIL.eq(email)))
            .describedAs("no half-made account is left behind")
            .isZero()
    }
}
