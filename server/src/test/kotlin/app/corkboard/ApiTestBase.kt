package app.corkboard

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.UUID
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

data class TestUser(val id: UUID, val email: String, val headers: HttpHeaders)

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class ApiTestBase {

    companion object {
        // Singleton container shared by every test class; Ryuk reaps it on JVM exit.
        @ServiceConnection
        @JvmStatic
        val db: PostgreSQLContainer<*> = PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                .asCompatibleSubstituteFor("postgres")
        ).apply { start() }
    }

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    protected fun json(res: ResponseEntity<String>): JsonNode = mapper.readTree(res.body)

    protected fun getJson(path: String, headers: HttpHeaders = HttpHeaders()): ResponseEntity<String> =
        rest.exchange(path, HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)

    protected fun sendJson(
        method: HttpMethod,
        path: String,
        body: Any?,
        headers: HttpHeaders = HttpHeaders(),
    ): ResponseEntity<String> {
        headers.contentType = MediaType.APPLICATION_JSON
        val payload = body?.let { mapper.writeValueAsString(it) }
        return rest.exchange(path, method, HttpEntity(payload, headers), String::class.java)
    }

    protected fun registerUser(displayName: String = "Resident"): TestUser {
        val email = "u-${UUID.randomUUID()}@example.com"
        val res = sendJson(
            HttpMethod.POST,
            "/api/v1/auth/register",
            mapOf("email" to email, "password" to "Pw-${UUID.randomUUID()}", "displayName" to displayName),
        )
        check(res.statusCode.value() == 201) { "register failed: ${res.body}" }
        val cookie = res.headers[HttpHeaders.SET_COOKIE]!!
            .first { it.startsWith("cb_session=") }
            .substringBefore(";")
        val id = UUID.fromString(json(res)["user"]["id"].asText())
        return TestUser(id, email, HttpHeaders().apply { add(HttpHeaders.COOKIE, cookie) })
    }
}
