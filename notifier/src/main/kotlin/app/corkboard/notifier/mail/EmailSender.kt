package app.corkboard.notifier.mail

data class OutgoingEmail(
    val id: String,
    val to: String,
    val subject: String,
    val text: String,
    val html: String?,
    val replyTo: String?,
)

interface EmailSender {
    val transport: String

    fun send(email: OutgoingEmail)
}
