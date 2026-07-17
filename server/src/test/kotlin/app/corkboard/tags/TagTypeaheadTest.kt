package app.corkboard.tags

import app.corkboard.ApiTestBase
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod

class TagTypeaheadTest : ApiTestBase() {

    @Test
    fun `typeahead prefix-matches and falls back to top by usage`() {
        val user = registerUser("Tagger")
        val marker = UUID.randomUUID().toString().take(6)
        fun create(tags: List<String>) {
            val res = sendJson(
                HttpMethod.POST, "/api/v1/events",
                mapOf(
                    "type" to "club",
                    "title" to "Tagged note ${UUID.randomUUID()}",
                    "body" to "Body.",
                    "location" to mapOf("lng" to 33.3, "lat" to 63.1),
                    "applyable" to false,
                    "expiresAt" to Instant.now().plus(15, ChronoUnit.DAYS).toString(),
                    "tags" to tags,
                ),
                user.headers,
            )
            check(res.statusCode.value() == 201) { res.body ?: "" }
        }

        create(listOf("Zx$marker Chess", "Zx$marker Cheese"))
        create(listOf("Zx$marker Chess"))
        create(listOf("Zx$marker Chess", "Zx$marker Cinema"))

        val byPrefix = json(getJson("/api/v1/tags?q=zx$marker che"))["items"]
        assertThat(byPrefix.map { it["slug"].asText() })
            .containsExactly("zx$marker-chess", "zx$marker-cheese")
        assertThat(byPrefix[0]["usageCount"].asInt()).isEqualTo(3)
        assertThat(byPrefix[0]["name"].asText()).isEqualTo("Zx$marker Chess")

        val top = json(getJson("/api/v1/tags?q=zx$marker&limit=2"))["items"]
        assertThat(top).hasSize(2)
        assertThat(top.map { it["slug"].asText() })
            .containsExactly("zx$marker-chess", "zx$marker-cheese")
    }

    @Test
    fun `typeahead is public and unfiltered query returns items`() {
        val res = getJson("/api/v1/tags")
        assertThat(res.statusCode.value()).isEqualTo(200)
        assertThat(json(res)["items"].isArray).isTrue
    }
}
