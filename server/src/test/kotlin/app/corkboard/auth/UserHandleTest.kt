package app.corkboard.auth

import app.corkboard.ApiTestBase
import app.corkboard.jooq.tables.references.USERS
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod

class UserHandleTest : ApiTestBase() {

    @Autowired
    lateinit var pending: PendingSignups

    private fun handle() = "h_${UUID.randomUUID().toString().replace("-", "").take(12)}"

    private fun register(
        handle: String,
        email: String = "handle-${UUID.randomUUID()}@example.com",
        displayName: String = "Handle Holder",
    ) = sendJson(
        HttpMethod.POST, "/api/v1/auth/register",
        mapOf(
            "email" to email,
            "password" to "Pw-${UUID.randomUUID()}",
            "displayName" to displayName,
            "handle" to handle,
        ),
    )

    @Test
    fun `a user id is taken once, whatever the case, and comes back lower case`() {
        val handle = handle()
        val first = register(handle.uppercase())

        assertThat(first.statusCode.value()).isEqualTo(201)
        assertThat(json(first)["user"]["handle"].asText())
            .describedAs("the id someone types is the id they get")
            .isEqualTo(handle)

        val again = register(handle)
        assertThat(again.statusCode.value()).isEqualTo(409)
        assertThat(json(again)["code"].asText()).isEqualTo("handle_taken")
    }

    @Test
    fun `a taken address is still its own answer`() {
        val email = "shared-${UUID.randomUUID()}@example.com"
        register(handle(), email = email)

        val again = register(handle(), email = email)

        assertThat(again.statusCode.value()).isEqualTo(409)
        assertThat(json(again)["code"].asText()).isEqualTo("email_taken")
    }

    @Test
    fun `shapes that are not user ids are refused`() {
        for (bad in listOf("ab", "a".repeat(31), "has space", "no-dashes", "punc!", "")) {
            val res = register(bad)
            assertThat(res.statusCode.value())
                .describedAs("\"%s\" is not a user id", bad)
                .isEqualTo(422)
        }
    }

    @Test
    fun `the id follows its owner onto notes and messages, and renaming leaves it alone`() {
        val author = registerUser("Author With Id")
        val handle = json(getJson("/api/v1/auth/me", author.headers))["user"]["handle"].asText()
        val eventId = createEvent(author, 31.1, 61.1, title = "Note by a known id")

        val detail = json(getJson("/api/v1/events/$eventId"))
        assertThat(detail["author"]["handle"].asText()).isEqualTo(handle)

        val renamed = sendJson(
            HttpMethod.PATCH, "/api/v1/auth/me",
            mapOf("displayName" to "Renamed Entirely"), author.headers,
        )
        assertThat(renamed.statusCode.value()).isEqualTo(200)
        assertThat(json(renamed)["user"]["handle"].asText())
            .describedAs("the user id does not move when the name does")
            .isEqualTo(handle)
    }

    @Test
    fun `a google sign-up writes no row until the user id arrives`() {
        val email = "pending-${UUID.randomUUID()}@example.com"
        val token = pending.issue(GoogleProfile("sub-${UUID.randomUUID()}", email, true, "Google Newcomer"))
        assertThat(dsl.fetchCount(USERS, USERS.EMAIL.eq(email))).isZero()

        val handle = handle()
        val completed = sendJson(
            HttpMethod.POST, "/api/v1/auth/google/complete",
            mapOf("token" to token, "displayName" to "Google Newcomer", "handle" to handle),
        )

        assertThat(completed.statusCode.value()).isEqualTo(201)
        assertThat(json(completed)["user"]["handle"].asText()).isEqualTo(handle)
        assertThat(json(completed)["user"]["emailVerified"].asBoolean())
            .describedAs("Google vouched for the address")
            .isTrue
        assertThat(dsl.fetchCount(USERS, USERS.EMAIL.eq(email))).isEqualTo(1)
    }

    @Test
    fun `a signup token that was not signed here is refused`() {
        val real = pending.issue(GoogleProfile("sub-x", "forged-${UUID.randomUUID()}@example.com", true, "Forger"))
        val forged = real.substringBefore('.') + ".AAAA" + real.substringAfter('.').drop(4)

        val res = sendJson(
            HttpMethod.POST, "/api/v1/auth/google/complete",
            mapOf("token" to forged, "displayName" to "Forger", "handle" to handle()),
        )

        assertThat(res.statusCode.value()).isEqualTo(422)
        assertThat(json(res)["code"].asText()).isEqualTo("signup_expired")
    }

    @Test
    fun `a user id claimed while the newcomer was choosing is a conflict, not a crash`() {
        val handle = handle()
        register(handle)
        val token = pending.issue(
            GoogleProfile("sub-${UUID.randomUUID()}", "racer-${UUID.randomUUID()}@example.com", true, "Racer"),
        )

        val res = sendJson(
            HttpMethod.POST, "/api/v1/auth/google/complete",
            mapOf("token" to token, "displayName" to "Racer", "handle" to handle),
        )

        assertThat(res.statusCode.value()).isEqualTo(409)
        assertThat(json(res)["code"].asText()).isEqualTo("handle_taken")
    }
}
