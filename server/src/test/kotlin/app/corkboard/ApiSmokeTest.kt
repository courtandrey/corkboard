package app.corkboard

import app.corkboard.jooq.tables.references.USERS
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ApiSmokeTest : ApiTestBase() {


    @Test
    fun `jooq generated schema queries the migrated database`() {
        assertThat(dsl.selectCount().from(USERS).fetchOne(0, Int::class.java)).isNotNull
    }

    @Test
    fun `health reports ok`() {
        val res = rest.getForEntity("/api/v1/health", Map::class.java)
        assertThat(res.statusCode.value()).isEqualTo(200)
        assertThat(res.body?.get("status")).isEqualTo("ok")
    }

    @Test
    fun `meta serves the taxonomy of every board and the config`() {
        val res = rest.getForEntity("/api/v1/meta", Map::class.java)
        assertThat(res.statusCode.value()).isEqualTo(200)

        val body = res.body!!
        @Suppress("UNCHECKED_CAST")
        val types = body["types"] as List<Map<String, Any>>
        assertThat(types.map { it["key"] }).containsExactly(
            "lost_found", "activity", "club", "help", "giveaway", "happening", "notice", "plan", "memory"
        )

        @Suppress("UNCHECKED_CAST")
        val scopes = body["scopes"] as List<Map<String, Any>>
        assertThat(scopes.map { it["key"] }).containsExactly("global", "personal")
        assertThat(scopes.first { it["key"] == "global" }["types"]).isEqualTo(
            listOf("lost_found", "activity", "club", "help", "giveaway", "happening", "notice")
        )
        assertThat(scopes.first { it["key"] == "personal" }["types"])
            .describedAs("a resident's own board keeps its own, smaller vocabulary")
            .isEqualTo(listOf("notice", "plan", "memory"))
        assertThat(types.first { it["key"] == "lost_found" }["color"]).isEqualTo("#D9822B")
        assertThat(body["reportThreshold"]).isEqualTo(5)
        assertThat(body["googleAuth"]).isEqualTo(false)
        assertThat(body["limits"]).isNotNull
    }

    @Test
    fun `openapi document is published at the versioned path`() {
        val res = rest.getForEntity("/api/v1/openapi.json", Map::class.java)
        assertThat(res.statusCode.value()).isEqualTo(200)
        assertThat(res.body?.get("openapi") as String).startsWith("3.1")
    }
}
