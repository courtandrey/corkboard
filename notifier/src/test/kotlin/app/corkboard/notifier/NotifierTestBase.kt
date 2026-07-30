package app.corkboard.notifier

import app.corkboard.notifications.avro.NotificationRequested
import app.corkboard.notifications.avro.NotificationType
import app.corkboard.notifications.avro.Recipient
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

const val TEST_API_KEY = "test-key"

/** Shared by every notifier context: the service owns a small database of its own. */
object NotifierDatabase {
    val container: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine").apply { start() }

    fun register(registry: DynamicPropertyRegistry) {
        registry.add("spring.datasource.url") { container.jdbcUrl }
        registry.add("spring.datasource.username") { container.username }
        registry.add("spring.datasource.password") { container.password }
    }
}

fun encode(
    idempotencyId: String,
    type: NotificationType = NotificationType.EMAIL_VERIFICATION,
    email: String = "resident@example.com",
    name: String? = "Marisol",
    variables: Map<String, String> = mapOf(
        "user_name" to "Marisol",
        "verification_link" to "https://board.example.com/api/v1/auth/verify?token=abc",
    ),
): ByteArray {
    return NotificationRequested.newBuilder()
        .setIdempotencyId(idempotencyId)
        .setType(type)
        .setRecipient(Recipient.newBuilder().setEmail(email).setName(name).build())
        .setVariables(variables)
        .build()
        .toByteBuffer()
        .array()
}

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "notifier.api-key=$TEST_API_KEY",
        "notifier.transport=smtp",
        "notifier.from=board@lamppostal.test",
        "notifier.from-name=Lamppostal",
        "notifier.max-attempts=2",
        "notifier.retry-backoff-millis=10",
        "notifier.kafka.enabled=false",
        "spring.mail.host=127.0.0.1",
        "spring.mail.port=3025",
        "spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false",
    ],
)
abstract class NotifierTestBase {

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun database(registry: DynamicPropertyRegistry) = NotifierDatabase.register(registry)
    }

    @Autowired
    protected lateinit var rest: TestRestTemplate

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    protected fun post(body: Any?, apiKey: String? = TEST_API_KEY): ResponseEntity<String> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            apiKey?.let { set("X-Api-Key", it) }
        }
        return rest.exchange("/api/v1/emails", HttpMethod.POST, HttpEntity(body, headers), String::class.java)
    }

    protected fun json(response: ResponseEntity<String>): JsonNode = objectMapper.readTree(response.body)
}
