package app.corkboard.notifier.mail

data class OutgoingEmail(
    val id: String,
    val idempotencyKey: String?,
    val to: String,
    val toName: String?,
    val subject: String,
    val text: String,
    val html: String?,
    val replyTo: String?,
)

class PermanentDeliveryException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

interface EmailSender {
    val transport: String

    val deduplicates: Boolean
        get() = false

    fun send(email: OutgoingEmail)
}
