package app.corkboard.notifications

import app.corkboard.notifications.avro.NotificationRequested
import app.corkboard.notifications.avro.NotificationType
import java.nio.ByteBuffer
import java.time.Duration
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.ByteArrayDeserializer
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

private const val TOPIC = "corkboard.notifications.v1"

@SpringBootTest(
    properties = [
        "corkboard.notifications.enabled=true",
        "corkboard.notifications.topic=$TOPIC",
    ],
)
@Testcontainers
class NotificationPublisherTest {

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
    lateinit var publisher: NotificationPublisher

    @Test
    fun `a notification lands on the topic as the avro record the notifier reads`() {
        publisher.publish(
            idempotencyId = "3f0b6f2e-verify",
            type = NotificationType.EMAIL_VERIFICATION,
            email = "resident@example.com",
            name = "Marisol",
            variables = mapOf(
                "user_name" to "Marisol",
                "verification_link" to "https://board.example.com/api/v1/auth/verify?token=abc",
            ),
        )

        consumer().use { consumer ->
            consumer.subscribe(listOf(TOPIC))
            val record = KafkaTestUtils.getSingleRecord(consumer, TOPIC, Duration.ofSeconds(30))

            assertThat(record.key()).isEqualTo("3f0b6f2e-verify")

            val event = NotificationRequested.fromByteBuffer(ByteBuffer.wrap(record.value()))

            assertThat(event.idempotencyId.toString()).isEqualTo("3f0b6f2e-verify")
            assertThat(event.type).isEqualTo(NotificationType.EMAIL_VERIFICATION)
            assertThat(event.recipient.email.toString()).isEqualTo("resident@example.com")
            assertThat(event.recipient.name.toString()).isEqualTo("Marisol")
            assertThat(event.variables.mapKeys { it.key.toString() }.mapValues { it.value.toString() })
                .containsExactlyInAnyOrderEntriesOf(
                    mapOf(
                        "user_name" to "Marisol",
                        "verification_link" to "https://board.example.com/api/v1/auth/verify?token=abc",
                    )
                )
        }
    }

    private fun consumer(): KafkaConsumer<String, ByteArray> {
        val props = KafkaTestUtils.consumerProps(kafka.bootstrapServers, "publisher-check", "true")
        props["key.deserializer"] = StringDeserializer::class.java
        props["value.deserializer"] = ByteArrayDeserializer::class.java
        return KafkaConsumer(props)
    }
}
