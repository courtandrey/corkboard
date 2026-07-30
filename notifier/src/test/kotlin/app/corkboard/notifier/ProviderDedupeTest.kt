package app.corkboard.notifier

import app.corkboard.notifier.jooq.tables.references.SENT_NOTIFICATIONS
import app.corkboard.notifier.mail.EmailSender
import app.corkboard.notifier.mail.OutgoingEmail
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.jooq.DSLContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer

private const val TOPIC = "corkboard.notifications.v1"

class DedupingSender : EmailSender {
    override val transport = "deduping"
    override val deduplicates = true

    val keys = CopyOnWriteArrayList<String?>()

    override fun send(email: OutgoingEmail) {
        keys += email.idempotencyKey
    }
}

@SpringBootTest(
    properties = [
        "notifier.api-key=$TEST_API_KEY",
        "notifier.from=board@lamppostal.test",
        "notifier.max-attempts=1",
        "notifier.kafka.enabled=true",
        "notifier.kafka.concurrency=1",
        "notifier.kafka.partitions=1",
    ],
)
@Testcontainers
class ProviderDedupeTest {

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

    @TestConfiguration
    class DedupingSenderConfig {
        @Bean
        @Primary
        fun dedupingSender(): DedupingSender = DedupingSender()
    }

    @Autowired
    lateinit var template: KafkaTemplate<String, Any>

    @Autowired
    lateinit var sender: DedupingSender

    @Autowired
    lateinit var dsl: DSLContext

    @Test
    fun `a provider that dedupes keeps the send path off the database`() {
        val id = "dedupe-${System.nanoTime()}"

        template.send(TOPIC, id, encode(id)).get()
        template.send(TOPIC, id, encode(id)).get()

        await().atMost(Duration.ofSeconds(30)).until { sender.keys.size == 2 }
        assertThat(sender.keys)
            .describedAs("both deliveries carry the same key, and the provider drops the repeat")
            .containsExactly(id, id)

        Thread.sleep(500)
        assertThat(dsl.fetchCount(SENT_NOTIFICATIONS, SENT_NOTIFICATIONS.IDEMPOTENCY_ID.eq(id)))
            .describedAs("no claim is written, so no connection is held while a message is in flight")
            .isZero()
    }
}
