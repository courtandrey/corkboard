package app.corkboard.notifier

import app.corkboard.notifier.mail.EmailSender
import app.corkboard.notifier.mail.OutgoingEmail
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
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
private const val SEND_CONCURRENCY = 8
private const val MESSAGES = 24
private const val SEND_MILLIS = 300L

class SlowSender : EmailSender {
    override val transport = "slow"

    val inFlight = AtomicInteger()
    val peakInFlight = AtomicInteger()
    val onVirtualThreads = AtomicInteger()
    val done = CountDownLatch(MESSAGES)

    override fun send(email: OutgoingEmail) {
        val now = inFlight.incrementAndGet()
        peakInFlight.updateAndGet { peak -> maxOf(peak, now) }
        if (Thread.currentThread().isVirtual) onVirtualThreads.incrementAndGet()
        try {
            Thread.sleep(SEND_MILLIS)
        } finally {
            inFlight.decrementAndGet()
            done.countDown()
        }
    }
}

@SpringBootTest(
    properties = [
        "notifier.api-key=$TEST_API_KEY",
        "notifier.from=board@lamppostal.test",
        "notifier.send-concurrency=$SEND_CONCURRENCY",
        "notifier.rate.per-second=1000",
        "notifier.kafka.enabled=true",
        "notifier.kafka.concurrency=1",
        "notifier.kafka.partitions=1",
    ],
)
@Testcontainers
class ParallelDeliveryTest {

    companion object {
        @Container
        @JvmStatic
        val kafka = KafkaContainer("apache/kafka:3.9.1")

        @DynamicPropertySource
        @JvmStatic
        fun kafkaProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers") { kafka.bootstrapServers }
            NotifierDatabase.register(registry)
        }
    }

    @TestConfiguration
    class SlowSenderConfig {
        @Bean
        @Primary
        fun slowSender(): SlowSender = SlowSender()
    }

    @Autowired
    lateinit var template: KafkaTemplate<String, Any>

    @Autowired
    lateinit var sender: SlowSender

    @Test
    fun `a batch goes out in parallel, bounded by send-concurrency, on virtual threads`() {
        val started = System.nanoTime()
        repeat(MESSAGES) { i ->
            template.send(TOPIC, "load:$i", encode("load:$i"))
        }
        template.flush()

        assertThat(sender.done.await(60, TimeUnit.SECONDS)).isTrue()
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000

        val sequentialMillis = MESSAGES * SEND_MILLIS
        assertThat(elapsedMillis)
            .describedAs("one consumer thread sending one at a time would need ${sequentialMillis}ms")
            .isLessThan(sequentialMillis / 3)

        assertThat(sender.peakInFlight.get())
            .describedAs("sends overlap")
            .isGreaterThan(1)
        assertThat(sender.peakInFlight.get())
            .describedAs("but never more than notifier.send-concurrency at once")
            .isLessThanOrEqualTo(SEND_CONCURRENCY)
        assertThat(sender.onVirtualThreads.get())
            .describedAs("every send runs on a virtual thread, not a pooled platform thread")
            .isEqualTo(MESSAGES)
    }
}
