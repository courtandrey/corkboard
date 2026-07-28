package app.corkboard.notifier

import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.GreenMailUtil
import com.icegreen.greenmail.util.ServerSetupTest
import java.time.Duration
import org.apache.kafka.clients.consumer.KafkaConsumer
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

private const val TOPIC = "corkboard.emails.v1"

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
class EmailEventConsumerTest {

    companion object {
        @Container
        @JvmStatic
        val kafka = KafkaContainer("apache/kafka:3.9.1")

        @DynamicPropertySource
        @JvmStatic
        fun kafkaProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers") { kafka.bootstrapServers }
        }
    }

    @RegisterExtension
    val smtp: GreenMailExtension = GreenMailExtension(ServerSetupTest.SMTP)

    @Autowired
    lateinit var template: KafkaTemplate<String, String>

    @Test
    fun `an event on the topic turns into an email`() {
        template.send(
            TOPIC,
            "verify:42",
            """
            {"to":"resident@example.com","subject":"Confirm your email",
             "text":"Open the link to confirm.","key":"verify:42"}
            """.trimIndent(),
        ).get()

        assertThat(smtp.waitForIncomingEmail(20_000, 1)).isTrue()
        val received = smtp.receivedMessages.single()
        assertThat(received.allRecipients.single().toString()).isEqualTo("resident@example.com")
        assertThat(received.subject).isEqualTo("Confirm your email")
        assertThat(GreenMailUtil.getBody(received)).contains("Open the link to confirm.")
    }

    @Test
    fun `an event the notifier cannot read lands in the dead letter topic`() {
        template.send(TOPIC, "broken:1", """{"subject":"No recipient","text":"nowhere to go"}""").get()

        consumer().use { consumer ->
            consumer.subscribe(listOf("$TOPIC.DLT"))
            val dead = KafkaTestUtils.getSingleRecord(consumer, "$TOPIC.DLT", Duration.ofSeconds(30))
            assertThat(dead.key()).isEqualTo("broken:1")
            assertThat(dead.value()).contains("No recipient")
        }
        assertThat(smtp.receivedMessages).isEmpty()
    }

    private fun consumer(): KafkaConsumer<String, String> {
        val props = KafkaTestUtils.consumerProps(kafka.bootstrapServers, "dlt-check", "true")
        props["key.deserializer"] = StringDeserializer::class.java
        props["value.deserializer"] = StringDeserializer::class.java
        props["auto.offset.reset"] = "earliest"
        return KafkaConsumer(props)
    }
}
