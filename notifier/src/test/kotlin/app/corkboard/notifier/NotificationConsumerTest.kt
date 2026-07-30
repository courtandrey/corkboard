package app.corkboard.notifier

import app.corkboard.notifications.avro.NotificationType
import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.GreenMailUtil
import com.icegreen.greenmail.util.ServerSetupTest
import java.time.Duration
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer

private const val TOPIC = "corkboard.notifications.v1"

@SpringBootTest(
    properties = [
        "notifier.api-key=$TEST_API_KEY",
        "notifier.transport=smtp",
        "notifier.from=board@lamppostal.test",
        "notifier.from-name=Lamppostal",
        "notifier.max-attempts=1",
        "notifier.kafka.enabled=true",
        "notifier.kafka.concurrency=1",
        "notifier.kafka.partitions=1",
        "notifier.kafka.max-attempts=2",
        "notifier.kafka.backoff-millis=200",
        "spring.mail.host=127.0.0.1",
        "spring.mail.port=3025",
        "spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false",
    ],
)
@Testcontainers
class NotificationConsumerTest {

    companion object {
        @Container
        @JvmStatic
        val kafka = KafkaContainer("apache/kafka:3.9.1")

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers") { kafka.bootstrapServers }
            NotifierDatabase.register(registry)
        }
    }

    @RegisterExtension
    val smtp: GreenMailExtension = GreenMailExtension(ServerSetupTest.SMTP)

    @Autowired
    lateinit var template: KafkaTemplate<String, Any>

    @Test
    fun `a verification event becomes the templated email`() {
        val id = "verify-${System.nanoTime()}"
        template.send(TOPIC, id, encode(id)).get()

        assertThat(smtp.waitForIncomingEmail(20_000, 1)).isTrue()
        val received = smtp.receivedMessages.single()

        assertThat(received.subject).isEqualTo("Confirm your email for Lamppostal")
        assertThat(received.allRecipients.single().toString()).isEqualTo("Marisol <resident@example.com>")

        val body = GreenMailUtil.getWholeMessage(received)
        assertThat(body).describedAs("greeting comes from the template").contains("Hi Marisol,")
        assertThat(body).contains("https://board.example.com/api/v1/auth/verify?token=abc")
        assertThat(body).describedAs("html alternative is the stored design").contains("lampp")
    }

    @Test
    fun `the same idempotency id is only ever sent once`() {
        val id = "verify-once-${System.nanoTime()}"
        template.send(TOPIC, id, encode(id)).get()
        assertThat(smtp.waitForIncomingEmail(20_000, 1)).isTrue()

        template.send(TOPIC, id, encode(id)).get()
        Thread.sleep(1_500)

        assertThat(smtp.receivedMessages)
            .describedAs("the redelivery was recognised")
            .hasSize(1)
    }

    @Test
    fun `an event this build cannot render goes to the dead letter topic`() {
        val id = "unknown-${System.nanoTime()}"
        template.send(TOPIC, id, encode(id, type = NotificationType.UNKNOWN)).get()

        val dead = awaitDeadLetter(id)
        assertThat(dead.headers().lastHeader("x-exception").value().decodeToString()).contains("unknown")
        assertThat(smtp.receivedMessages).isEmpty()
    }

    @Test
    fun `a payload that is not avro at all goes to the dead letter topic`() {
        val id = "garbage-${System.nanoTime()}"
        template.send(TOPIC, id, "this is not avro".toByteArray()).get()

        val dead = awaitDeadLetter(id)
        assertThat(dead.headers().lastHeader("x-exception").value().decodeToString())
            .contains("not a NotificationRequested record")
        assertThat(smtp.receivedMessages).isEmpty()
    }

    private fun awaitDeadLetter(key: String): ConsumerRecord<String, ByteArray> {
        consumer().use { consumer ->
            consumer.subscribe(listOf("$TOPIC.DLT"))
            val deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
            while (System.nanoTime() < deadline) {
                val found = consumer.poll(Duration.ofMillis(500)).firstOrNull { it.key() == key }
                if (found != null) return found
            }
            throw AssertionError("no dead letter for $key")
        }
    }

    private fun consumer(): KafkaConsumer<String, ByteArray> {
        val props = KafkaTestUtils.consumerProps(kafka.bootstrapServers, "dlt-check-${System.nanoTime()}", "true")
        props["key.deserializer"] = StringDeserializer::class.java
        props["value.deserializer"] = ByteArrayDeserializer::class.java
        return KafkaConsumer(props)
    }
}
