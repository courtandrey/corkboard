package app.corkboard.notifier.mail

import app.corkboard.notifier.config.NotifierProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"

private const val TOO_MANY_REQUESTS = 429

@JsonInclude(JsonInclude.Include.NON_NULL)
private data class ResendMessage(
    val from: String,
    val to: List<String>,
    val subject: String,
    val text: String,
    val html: String?,
    @get:JsonProperty("reply_to") val replyTo: String?,
)

private data class ResendAccepted(val id: String?)

class ResendEmailSender(
    private val http: RestClient,
    private val props: NotifierProperties,
) : EmailSender {

    private val log = LoggerFactory.getLogger(javaClass)

    override val transport = "resend"

    override val deduplicates = true

    override fun send(email: OutgoingEmail) {
        val accepted = try {
            http.post()
                .uri("/emails")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${props.resend.apiKey}")
                .header(IDEMPOTENCY_KEY_HEADER, email.idempotencyKey ?: email.id)
                .body(message(email))
                .retrieve()
                .body(ResendAccepted::class.java)
        } catch (refused: RestClientResponseException) {
            throw refusal(refused)
        }
        log.debug("resend accepted {} as {}", email.id, accepted?.id)
    }

    private fun message(email: OutgoingEmail) = ResendMessage(
        from = props.fromName.ifBlank { null }?.let { "$it <${props.sender}>" } ?: props.sender,
        to = listOf(email.toName?.let { "$it <${email.to}>" } ?: email.to),
        subject = email.subject,
        text = email.text,
        html = email.html,
        replyTo = email.replyTo ?: props.replyTo.ifBlank { null },
    )

    private fun refusal(refused: RestClientResponseException): RuntimeException {
        val status = refused.statusCode
        if (status.is4xxClientError && status.value() != TOO_MANY_REQUESTS) {
            return PermanentDeliveryException(
                "resend refused the message with ${status.value()}: ${refused.responseBodyAsString.take(500)}",
                refused,
            )
        }
        return refused
    }
}
