package app.corkboard.notifier.mail

import app.corkboard.notifier.api.ApiException
import app.corkboard.notifier.api.ProblemCode
import app.corkboard.notifier.config.NotifierProperties
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

@Service
class EmailService(
    private val sender: EmailSender,
    private val props: NotifierProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    val transport: String
        get() = sender.transport

    fun deliver(
        to: String,
        subject: String,
        text: String,
        html: String?,
        replyTo: String?,
        key: String?,
    ): String {
        checkSize("subject", subject.length, props.limits.subjectMax)
        checkSize("text", text.length, props.limits.textMax)
        html?.let { checkSize("html", it.length, props.limits.htmlMax) }

        val email = OutgoingEmail(UUID.randomUUID().toString(), to, subject, text, html, replyTo)
        val started = System.nanoTime()

        var attempt = 1
        while (true) {
            try {
                sender.send(email)
                log.info(
                    "sent {} to {} via {} in {}ms (attempt {}{})",
                    email.id, mask(to), sender.transport,
                    (System.nanoTime() - started) / 1_000_000, attempt,
                    key?.let { ", key=$it" } ?: "",
                )
                return email.id
            } catch (failure: Exception) {
                if (attempt >= props.maxAttempts) {
                    log.error("giving up on {} to {} after {} attempts", email.id, mask(to), attempt, failure)
                    throw ApiException(HttpStatus.BAD_GATEWAY, ProblemCode.DELIVERY_FAILED, failure)
                }
                log.warn("attempt {} for {} failed: {}", attempt, email.id, failure.message)
                Thread.sleep(props.retryBackoffMillis * attempt)
                attempt++
            }
        }
    }

    private fun checkSize(field: String, length: Int, max: Int) {
        if (length > max) {
            throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.VALIDATION_FAILED, fields = mapOf(field to "must be at most $max characters"))
        }
    }

    private fun mask(address: String): String {
        val at = address.indexOf('@')
        if (at <= 1) return "***"
        return "${address.first()}***${address.substring(at)}"
    }
}
