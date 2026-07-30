package app.corkboard.notifier

import app.corkboard.notifier.jooq.tables.references.SENT_NOTIFICATIONS
import app.corkboard.notifier.mail.EmailSender
import app.corkboard.notifier.mail.OutgoingEmail
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
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

class FlakySender : EmailSender {
    override val transport = "flaky"

    val attempts = AtomicInteger()
    val delivered = AtomicInteger()

    override fun send(email: OutgoingEmail) {
        if (attempts.incrementAndGet() == 1) throw IllegalStateException("provider said no")
        delivered.incrementAndGet()
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
class TransactionalSendTest {

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
    class FlakySenderConfig {
        @Bean
        @Primary
        fun flakySender(): FlakySender = FlakySender()
    }

    @Autowired
    lateinit var template: KafkaTemplate<String, Any>

    @Autowired
    lateinit var sender: FlakySender

    @Autowired
    lateinit var dsl: DSLContext

    @Test
    fun `a failed send leaves no claim, so its dead letter can be replayed exactly once`() {
        val id = "flaky-${System.nanoTime()}"

        // the sender refuses this one: the transaction rolls back and the record is dead-lettered
        template.send(TOPIC, id, encode(id)).get()
        await().atMost(Duration.ofSeconds(30)).until { sender.attempts.get() == 1 }
        Thread.sleep(500)
        assertThat(rowsFor(id))
            .describedAs("a send that never happened is not recorded")
            .isZero()

        // replaying the dead letter reaches the provider, because nothing claims the id
        template.send(TOPIC, id, encode(id)).get()
        await().atMost(Duration.ofSeconds(30)).until { sender.delivered.get() == 1 }
        assertThat(rowsFor(id)).isEqualTo(1)

        // and any further redelivery finds the committed claim and stops
        template.send(TOPIC, id, encode(id)).get()
        Thread.sleep(1_500)
        assertThat(sender.delivered.get()).isEqualTo(1)
        assertThat(sender.attempts.get()).isEqualTo(2)
        assertThat(rowsFor(id)).isEqualTo(1)
    }

    private fun rowsFor(id: String): Int =
        dsl.fetchCount(SENT_NOTIFICATIONS, SENT_NOTIFICATIONS.IDEMPOTENCY_ID.eq(id))
}
