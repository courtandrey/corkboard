package app.corkboard.notifier.kafka

import app.corkboard.notifications.avro.NotificationRequested
import app.corkboard.notifier.api.ApiException
import app.corkboard.notifier.notifications.Handled
import app.corkboard.notifier.notifications.NotificationHandler
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import org.apache.commons.logging.LogFactory
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.log.LogAccessor
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.serializer.SerializationUtils
import org.springframework.stereotype.Component

private typealias Notification = ConsumerRecord<String, NotificationRequested?>

@Component
@ConditionalOnProperty(prefix = "notifier.kafka", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class NotificationListener(
    private val handler: NotificationHandler,
    private val deadLetters: DeadLetterPublisher,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val headers = LogAccessor(LogFactory.getLog(javaClass))

    @KafkaListener(
        topics = ["\${notifier.kafka.topic}"],
        groupId = "\${notifier.kafka.group}",
        concurrency = "\${notifier.kafka.concurrency}",
    )
    fun onBatch(records: List<Notification>) {
        val started = System.nanoTime()
        val inFlight = ArrayList<Pair<Notification, CompletableFuture<Handled>>>(records.size)
        var rejected = 0

        for (record in records) {
            val event = record.value()
            if (event == null) {
                deadLetters.publish(record, unreadable(record))
                rejected++
                continue
            }
            try {
                inFlight += record to handler.handle(event)
            } catch (invalid: InvalidNotificationException) {
                deadLetters.publish(record, invalid)
                rejected++
            } catch (refused: ApiException) {
                deadLetters.publish(record, refused)
                rejected++
            }
        }

        var sent = 0
        var duplicates = 0
        var failed = 0
        for ((record, flight) in inFlight) {
            try {
                if (flight.join().duplicate) duplicates++ else sent++
            } catch (failure: CompletionException) {
                deadLetters.publish(record, failure.cause ?: failure)
                failed++
            }
        }

        log.info(
            "batch of {} handled in {}ms — sent {}, duplicates {}, rejected {}, undeliverable {}",
            records.size, (System.nanoTime() - started) / 1_000_000, sent, duplicates, rejected, failed,
        )
    }

    private fun unreadable(record: Notification): Throwable =
        SerializationUtils.getExceptionFromHeader(
            record,
            SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER,
            headers,
        ) ?: InvalidNotificationException("record has no value")
}
