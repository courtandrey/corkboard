package app.corkboard

import app.corkboard.jooq.tables.references.USERS
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ApiSmokeTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val db = PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                .asCompatibleSubstituteFor("postgres")
        )
    }

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var dsl: DSLContext

    @Test
    fun `jooq generated schema queries the migrated database`() {
        assertThat(dsl.fetchCount(USERS)).isEqualTo(0)
    }

    @Test
    fun `health reports ok`() {
        val res = rest.getForEntity("/api/v1/health", Map::class.java)
        assertThat(res.statusCode.value()).isEqualTo(200)
        assertThat(res.body?.get("status")).isEqualTo("ok")
    }

    @Test
    fun `meta serves the seven-type taxonomy and config`() {
        val res = rest.getForEntity("/api/v1/meta", Map::class.java)
        assertThat(res.statusCode.value()).isEqualTo(200)

        val body = res.body!!
        @Suppress("UNCHECKED_CAST")
        val types = body["types"] as List<Map<String, Any>>
        assertThat(types).hasSize(7)
        assertThat(types.map { it["key"] }).containsExactly(
            "lost_found", "activity", "club", "help", "giveaway", "happening", "notice"
        )
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
