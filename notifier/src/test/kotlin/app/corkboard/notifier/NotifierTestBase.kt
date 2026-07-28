package app.corkboard.notifier

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

const val TEST_API_KEY = "test-key"

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

    @Autowired
    protected lateinit var rest: TestRestTemplate

    @Autowired
    protected lateinit var objectMapper: com.fasterxml.jackson.databind.ObjectMapper

    protected fun post(body: Any?, apiKey: String? = TEST_API_KEY): ResponseEntity<String> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            apiKey?.let { set("X-Api-Key", it) }
        }
        return rest.exchange("/api/v1/emails", HttpMethod.POST, HttpEntity(body, headers), String::class.java)
    }

    protected fun json(response: ResponseEntity<String>): JsonNode = objectMapper.readTree(response.body)
}
