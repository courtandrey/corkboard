package app.corkboard.notifier

import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.ServerSetupTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "notifier.api-key=$TEST_API_KEY",
        "notifier.transport=smtp",
        "notifier.from=board@lamppostal.test",
        "notifier.kafka.enabled=false",
        "notifier.rate.per-second=1",
        "notifier.rate.burst=1",
        "notifier.rate.http-max-wait-millis=0",
        "spring.mail.host=127.0.0.1",
        "spring.mail.port=3025",
        "spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false",
    ],
)
class RateLimitedApiTest {

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun database(registry: DynamicPropertyRegistry) = NotifierDatabase.register(registry)
    }

    @RegisterExtension
    val smtp: GreenMailExtension = GreenMailExtension(ServerSetupTest.SMTP)

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `a caller that arrives without a free permit is told to come back`() {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-Api-Key", TEST_API_KEY)
        }
        val body = mapOf("to" to "resident@example.com", "subject" to "One", "text" to "First one through.")
        val request = HttpEntity(body, headers)

        val first = rest.exchange("/api/v1/emails", HttpMethod.POST, request, String::class.java)
        assertThat(first.statusCode.value()).isEqualTo(202)

        val second = rest.exchange("/api/v1/emails", HttpMethod.POST, request, String::class.java)
        assertThat(second.statusCode.value()).isEqualTo(429)
        assertThat(objectMapper.readTree(second.body)["code"].asText()).isEqualTo("rate_limited")

        assertThat(smtp.waitForIncomingEmail(5_000, 1)).isTrue()
        assertThat(smtp.receivedMessages)
            .describedAs("the rejected call never reached the provider")
            .hasSize(1)
    }
}
