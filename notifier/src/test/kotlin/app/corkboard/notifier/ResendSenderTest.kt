package app.corkboard.notifier

import app.corkboard.notifier.config.NotifierProperties
import app.corkboard.notifier.config.NotifierProperties.Transport
import app.corkboard.notifier.mail.IDEMPOTENCY_KEY_HEADER
import app.corkboard.notifier.mail.OutgoingEmail
import app.corkboard.notifier.mail.PermanentDeliveryException
import app.corkboard.notifier.mail.ResendEmailSender
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

class ResendSenderTest {

    private val props = NotifierProperties(
        apiKey = TEST_API_KEY,
        transport = Transport.RESEND,
        from = "board@lamppostal.test",
        fromName = "Lamppostal",
        resend = NotifierProperties.Resend(apiKey = "re_test_key", baseUrl = "https://api.resend.test"),
    )

    private val builder: RestClient.Builder = RestClient.builder().baseUrl(props.resend.baseUrl)
    private val server: MockRestServiceServer = MockRestServiceServer.bindTo(builder).build()
    private val sender = ResendEmailSender(builder.build(), props)

    private val email = OutgoingEmail(
        id = "local-1",
        idempotencyKey = "email_verification/42",
        to = "resident@example.com",
        toName = "Marisol",
        subject = "Confirm your email",
        text = "Tap the link.",
        html = "<p>Tap the link.</p>",
        replyTo = null,
    )

    @Test
    fun `the notification's idempotency key travels with the message`() {
        server.expect(requestTo("https://api.resend.test/emails"))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(header(IDEMPOTENCY_KEY_HEADER, "email_verification/42"))
            .andExpect(header("Authorization", "Bearer re_test_key"))
            .andExpect(
                content().json(
                    """
                    {
                      "from": "Lamppostal <board@lamppostal.test>",
                      "to": ["Marisol <resident@example.com>"],
                      "subject": "Confirm your email",
                      "text": "Tap the link.",
                      "html": "<p>Tap the link.</p>"
                    }
                    """,
                ),
            )
            .andRespond(withSuccess("""{"id":"6a1b-resend"}""", MediaType.APPLICATION_JSON))

        sender.send(email)

        server.verify()
        assertThat(sender.deduplicates).describedAs("the provider owns the dedupe").isTrue()
    }

    @Test
    fun `a key reused with a different payload is refused for good`() {
        server.expect(requestTo("https://api.resend.test/emails")).andRespond(
            withStatus(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"name":"invalid_idempotent_request","message":"this idempotency key has already been used"}"""),
        )

        assertThatThrownBy { sender.send(email) }
            .isInstanceOf(PermanentDeliveryException::class.java)
            .hasMessageContaining("409")
    }

    @Test
    fun `a rate limit is left for the retry loop`() {
        server.expect(requestTo("https://api.resend.test/emails"))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))

        assertThatThrownBy { sender.send(email) }
            .isInstanceOf(RestClientResponseException::class.java)
            .isNotInstanceOf(PermanentDeliveryException::class.java)
    }
}
