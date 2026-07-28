package app.corkboard.notifications

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Duration
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName

private const val TOPIC = "corkboard.emails.v1"

@SpringBootTest(
    properties = [
        "corkboard.notifications.enabled=true",
        "corkboard.notifications.topic=$TOPIC",
    ],
)
@Testcontainers
class EmailRequestPublisherTest {

    companion object {
        @Container
        @JvmStatic
        val kafka = KafkaContainer("apache/kafka:3.9.1")

        @JvmStatic
        val db: PostgreSQLContainer<*> = PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                .asCompatibleSubstituteFor("postgres")
        ).apply { start() }

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers") { kafka.bootstrapServers }
            registry.add("spring.datasource.url") { db.jdbcUrl }
            registry.add("spring.datasource.username") { db.username }
            registry.add("spring.datasource.password") { db.password }
        }
    }

    @Autowired
    lateinit var publisher: EmailRequestPublisher

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `an email request lands on the topic in the shape the notifier reads`() {
        publisher.publish(
            EmailRequested(
                to = "resident@example.com",
                subject = "Confirm your email",
                text = "Open the link to confirm.",
                html = "<p>Open the link to confirm.</p>",
                key = "verify:7",
            )
        )

        consumer().use { consumer ->
            consumer.subscribe(listOf(TOPIC))
            val record = KafkaTestUtils.getSingleRecord(consumer, TOPIC, Duration.ofSeconds(30))
            assertThat(record.key()).isEqualTo("verify:7")

            val payload = objectMapper.readTree(record.value())
            assertThat(payload["to"].asText()).isEqualTo("resident@example.com")
            assertThat(payload["subject"].asText()).isEqualTo("Confirm your email")
            assertThat(payload["text"].asText()).isEqualTo("Open the link to confirm.")
            assertThat(payload["html"].asText()).isEqualTo("<p>Open the link to confirm.</p>")
            assertThat(payload["key"].asText()).isEqualTo("verify:7")
            assertThat(payload.has("replyTo")).describedAs("absent fields stay off the wire").isFalse()
        }
    }

    private fun consumer(): KafkaConsumer<String, String> {
        val props = KafkaTestUtils.consumerProps(kafka.bootstrapServers, "publisher-check", "true")
        props["key.deserializer"] = StringDeserializer::class.java
        props["value.deserializer"] = StringDeserializer::class.java
        return KafkaConsumer(props)
    }
}
