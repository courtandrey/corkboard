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

    private fun insertPasswordUser(email: String): UUID =
        dsl.insertInto(USERS)
            .set(USERS.EMAIL, email)
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
        val first = identity.createOrLink(GoogleProfile(sub, email, true, "Googler"))
        val again = identity.createOrLink(GoogleProfile(sub, "changed-$email", true, "Renamed"))

        val id = (first as GoogleIdentityResult.SignedIn).userId
        assertThat((again as GoogleIdentityResult.SignedIn).userId).isEqualTo(id)
        assertThat(dsl.fetchCount(USERS, USERS.GOOGLE_SUB.eq(sub))).isEqualTo(1)
    }

    @Test
    fun `verified email links an existing password account`() {
        val email = uniqueEmail()
        val existing = insertPasswordUser(email)
        val sub = "sub-${UUID.randomUUID()}"

        val result = identity.createOrLink(GoogleProfile(sub, email, true, "Googler"))

        assertThat((result as GoogleIdentityResult.SignedIn).userId).isEqualTo(existing)
        assertThat(googleSub(existing)).isEqualTo(sub)
    }

    @Test
    fun `unverified email never links an existing account`() {
        val email = uniqueEmail()
        val existing = insertPasswordUser(email)

        val result = identity.createOrLink(GoogleProfile("sub-${UUID.randomUUID()}", email, false, "Impostor"))

        assertThat(result).isEqualTo(GoogleIdentityResult.EmailConflict)
        assertThat(googleSub(existing)).isNull()
    }

    @Test
    fun `unknown identity creates a passwordless user with a truncated display name`() {
        val email = uniqueEmail()
        val longName = "N".repeat(80)

        val result = identity.createOrLink(GoogleProfile("sub-${UUID.randomUUID()}", email, true, longName))

        val row = dsl.selectFrom(USERS)
            .where(USERS.ID.eq((result as GoogleIdentityResult.SignedIn).userId))
            .fetchOne()!!
        assertThat(row.passwordHash).isNull()
        assertThat(row.displayName).hasSize(50)
        assertThat(row.email).isEqualTo(email)
    }
}
