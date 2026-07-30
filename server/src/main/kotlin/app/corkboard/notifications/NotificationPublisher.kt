package app.corkboard.notifications

import app.corkboard.common.CorkboardProperties
import app.corkboard.notifications.avro.NotificationRequested
import app.corkboard.notifications.avro.NotificationType
import app.corkboard.notifications.avro.Recipient
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class NotificationPublisher(
    private val kafka: KafkaTemplate<String, NotificationRequested>,
    private val props: CorkboardProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun publish(
        idempotencyId: String,
        type: NotificationType,
        email: String,
        name: String?,
        variables: Map<String, String>,
    ) {
        if (!props.notifications.enabled) {
            log.debug("notifications are off — dropping {} for {}", type, idempotencyId)
            return
        }

        val event = NotificationRequested.newBuilder()
            .setIdempotencyId(idempotencyId)
            .setType(type)
            .setRecipient(Recipient.newBuilder().setEmail(email).setName(name).build())
            .setVariables(variables)
            .build()

        val record = ProducerRecord(props.notifications.topic, idempotencyId, event)

        kafka.send(record).whenComplete { result, failure ->
            if (failure != null) {
                log.error("could not queue {} for {}", type, idempotencyId, failure)
            } else {
                val meta = result.recordMetadata
                log.info("queued {} {} to {}-{}@{}", type, idempotencyId, meta.topic(), meta.partition(), meta.offset())
            }
        }
    }
}
