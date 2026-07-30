package app.corkboard.notifier.kafka

import app.corkboard.notifications.avro.NotificationRequested
import app.corkboard.notifier.config.NotifierProperties
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.serializer.DeserializationException
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "notifier.kafka", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class DeadLetterPublisher(
    private val template: KafkaTemplate<String, Any>,
    private val props: NotifierProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun publish(record: ConsumerRecord<String, NotificationRequested?>, reason: Throwable) {
        val dead = ProducerRecord<String, Any>(props.kafka.deadLetterTopic, record.key(), payload(record, reason))
        dead.headers().add("x-original-topic", record.topic().toByteArray(StandardCharsets.UTF_8))
        dead.headers().add("x-original-partition", record.partition().toString().toByteArray(StandardCharsets.UTF_8))
        dead.headers().add("x-original-offset", record.offset().toString().toByteArray(StandardCharsets.UTF_8))
        dead.headers().add("x-exception", describe(reason).take(400).toByteArray(StandardCharsets.UTF_8))

        template.send(dead).get(10, TimeUnit.SECONDS)
        log.warn(
            "dead-lettered {}-{}@{} key={}: {}",
            record.topic(), record.partition(), record.offset(), record.key() ?: "-",
            describe(reason),
        )
    }

    private fun describe(reason: Throwable): String {
        val root = (reason as? DeserializationException)?.cause ?: reason
        return root.message ?: root.javaClass.simpleName
    }

    private fun payload(record: ConsumerRecord<String, NotificationRequested?>, reason: Throwable): Any? =
        record.value() ?: (reason as? DeserializationException)?.data
}
