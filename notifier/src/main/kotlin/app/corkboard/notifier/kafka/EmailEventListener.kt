package app.corkboard.notifier.kafka

import app.corkboard.notifier.api.ApiException
import app.corkboard.notifier.mail.EmailDispatcher
import app.corkboard.notifier.mail.EmailRequest
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "notifier.kafka", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class EmailEventListener(
    private val dispatcher: EmailDispatcher,
    private val deadLetters: DeadLetterPublisher,
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["\${notifier.kafka.topic}"],
        groupId = "\${notifier.kafka.group}",
        concurrency = "\${notifier.kafka.concurrency}",
    )
    fun onBatch(records: List<ConsumerRecord<String, String>>) {
        val started = System.nanoTime()
        val inFlight = ArrayList<Pair<ConsumerRecord<String, String>, CompletableFuture<String>>>(records.size)
        var rejected = 0

        for (record in records) {
            val request = read(record)
            if (request == null) {
                rejected++
                continue
            }
            try {
                inFlight += record to dispatcher.dispatch(request)
            } catch (refused: ApiException) {
                deadLetters.publish(record, refused)
                rejected++
            }
        }

        var sent = 0
        var failed = 0
        for ((record, flight) in inFlight) {
            try {
                flight.join()
                sent++
            } catch (failure: CompletionException) {
                deadLetters.publish(record, failure.cause ?: failure)
                failed++
            }
        }

        log.info(
            "batch of {} handled in {}ms — sent {}, rejected {}, undeliverable {}",
            records.size, (System.nanoTime() - started) / 1_000_000, sent, rejected, failed,
        )
    }

    private fun read(record: ConsumerRecord<String, String>): EmailRequest? =
        try {
            objectMapper.readEmailEvent(record.value(), record.key())
        } catch (invalid: InvalidEmailEventException) {
            deadLetters.publish(record, invalid)
            null
        }
}
