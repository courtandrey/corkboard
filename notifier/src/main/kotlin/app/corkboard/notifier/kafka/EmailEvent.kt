package app.corkboard.notifier.kafka

import app.corkboard.notifier.mail.EmailRequest
import com.fasterxml.jackson.databind.ObjectMapper

class InvalidEmailEventException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

data class EmailEvent(
    val to: String? = null,
    val subject: String? = null,
    val text: String? = null,
    val html: String? = null,
    val replyTo: String? = null,
    val key: String? = null,
)

private val ADDRESS = Regex("^[^@\\s]+@[^@\\s.]+\\.[^@\\s]+$")

fun ObjectMapper.readEmailEvent(payload: String?, fallbackKey: String?): EmailRequest {
    if (payload.isNullOrBlank()) throw InvalidEmailEventException("empty payload")
    val event = try {
        readValue(payload, EmailEvent::class.java)
    } catch (e: Exception) {
        throw InvalidEmailEventException("payload is not a valid email event: ${e.message}", e)
    }

    val to = event.to?.trim().orEmpty()
    val subject = event.subject?.trim().orEmpty()
    val text = event.text.orEmpty()
    if (!ADDRESS.matches(to)) throw InvalidEmailEventException("'to' is not an email address")
    if (subject.isEmpty()) throw InvalidEmailEventException("'subject' is missing")
    if (text.isEmpty()) throw InvalidEmailEventException("'text' is missing")

    return EmailRequest(
        to = to,
        subject = subject,
        text = text,
        html = event.html,
        replyTo = event.replyTo?.trim()?.takeIf { it.isNotEmpty() },
        key = event.key ?: fallbackKey,
    )
}
