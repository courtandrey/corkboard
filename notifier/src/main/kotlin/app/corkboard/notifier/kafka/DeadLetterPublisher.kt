package app.corkboard.notifier.kafka

import app.corkboard.notifier.config.NotifierProperties
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "notifier.kafka", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class DeadLetterPublisher(
    private val template: KafkaTemplate<String, String>,
    private val props: NotifierProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun publish(record: ConsumerRecord<String, String>, reason: Throwable) {
        val dead = ProducerRecord(props.kafka.deadLetterTopic, record.key(), record.value())
        dead.headers().add("x-original-topic", record.topic().toByteArray(StandardCharsets.UTF_8))
        dead.headers().add("x-original-partition", record.partition().toString().toByteArray(StandardCharsets.UTF_8))
        dead.headers().add("x-original-offset", record.offset().toString().toByteArray(StandardCharsets.UTF_8))
        dead.headers().add("x-exception", (reason.message ?: reason.javaClass.simpleName).take(400).toByteArray(StandardCharsets.UTF_8))

        template.send(dead).get(10, TimeUnit.SECONDS)
        log.warn(
            "dead-lettered {}-{}@{} key={}: {}",
            record.topic(), record.partition(), record.offset(), record.key() ?: "-",
            reason.message ?: reason.javaClass.simpleName,
        )
    }
}
