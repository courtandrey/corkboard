package app.corkboard.common

import app.corkboard.ApiTestBase
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpaDeepLinkTest : ApiTestBase() {

    @Test
    fun `every route the app owns answers with the app shell`() {
        val someone = UUID.randomUUID()
        val note = UUID.randomUUID()

        val routes = listOf(
            "/",
            "/new",
            "/login",
        "/finish-signup",
            "/events/$note",
            "/boards/$someone",
            "/boards/$someone/new",
            "/boards/$someone/events/$note",
            "/me/pins",
            "/me/account",
            "/messages",
            "/messages/$note",
            "/admin/reports",
            "/admin/features",
        )

        val answers = routes.associateWith { getJson(it) }

        assertThat(answers.filterValues { it.statusCode.value() != 200 }.keys)
            .describedAs("a signed-out visitor gets the shell, and the app decides what to show")
            .isEmpty()
        assertThat(answers.values.map { it.body })
            .allMatch { it != null && it.contains("corkboard-spa-shell") }
    }

    @Test
    fun `the API underneath is not made public by the routes above it`() {
        val someone = UUID.randomUUID()

        val board = getJson("/api/v1/boards/$someone/events?bbox=1,1,2,2&zoom=13")
        val mine = getJson("/api/v1/me/events")

        assertThat(board.statusCode.value()).isEqualTo(401)
        assertThat(mine.statusCode.value()).isEqualTo(401)
    }
}
