package app.corkboard.notifications

import app.corkboard.common.CorkboardProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@JsonInclude(JsonInclude.Include.NON_NULL)
data class EmailRequested(
    val to: String,
    val subject: String,
    val text: String,
    val html: String? = null,
    val replyTo: String? = null,
    val key: String? = null,
)

@Service
class EmailRequestPublisher(
    private val kafka: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val props: CorkboardProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun publish(request: EmailRequested) {
        if (!props.notifications.enabled) {
            log.debug("notifications are off — dropping the email for key {}", request.key ?: "-")
            return
        }
        val payload = objectMapper.writeValueAsString(request)
        kafka.send(ProducerRecord(props.notifications.topic, request.key, payload))
            .whenComplete { result, failure ->
                if (failure != null) {
                    log.error("could not queue the email for key {}", request.key ?: "-", failure)
                } else {
                    val meta = result.recordMetadata
                    log.info(
                        "queued email key={} to {}-{}@{}",
                        request.key ?: "-", meta.topic(), meta.partition(), meta.offset(),
                    )
                }
            }
    }
}
