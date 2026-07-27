package app.corkboard.notifier

import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.GreenMailUtil
import com.icegreen.greenmail.util.ServerSetupTest
import jakarta.mail.internet.MimeMultipart
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class SendEmailTest : NotifierTestBase() {

    @RegisterExtension
    val smtp: GreenMailExtension = GreenMailExtension(ServerSetupTest.SMTP)

    @Test
    fun `a plain text message reaches the mail server`() {
        val response = post(
            mapOf(
                "to" to "resident@example.com",
                "subject" to "Your note is about to expire",
                "text" to "Renew it from the board.",
                "key" to "expiry-42",
            ),
        )

        assertThat(response.statusCode.value()).isEqualTo(202)
        val body = json(response)
        assertThat(body["transport"].asText()).isEqualTo("smtp")
        assertThat(body["key"].asText()).isEqualTo("expiry-42")
        assertThat(body["id"].asText()).isNotBlank()

        assertThat(smtp.waitForIncomingEmail(5_000, 1)).isTrue()
        val received = smtp.receivedMessages.single()
        assertThat(received.allRecipients.single().toString()).isEqualTo("resident@example.com")
        assertThat(received.subject).isEqualTo("Your note is about to expire")
        assertThat(received.from.single().toString()).isEqualTo("Lamppostal <board@lamppostal.test>")
        assertThat(GreenMailUtil.getBody(received)).contains("Renew it from the board.")
        assertThat(received.getHeader("X-Corkboard-Message-Id").single()).isEqualTo(body["id"].asText())
    }

    @Test
    fun `an html message carries both alternatives and the reply-to`() {
        val response = post(
            mapOf(
                "to" to "resident@example.com",
                "subject" to "Confirm your email",
                "text" to "Open the link to confirm.",
                "html" to "<p>Open the <a href=\"https://board.example.com/confirm\">link</a> to confirm.</p>",
                "replyTo" to "keepers@lamppostal.test",
            ),
        )
        assertThat(response.statusCode.value()).isEqualTo(202)

        assertThat(smtp.waitForIncomingEmail(5_000, 1)).isTrue()
        val received = smtp.receivedMessages.single()
        assertThat(received.replyTo.single().toString()).isEqualTo("keepers@lamppostal.test")
        assertThat(received.content).isInstanceOf(MimeMultipart::class.java)
        val body = GreenMailUtil.getWholeMessage(received)
        assertThat(body).contains("Open the link to confirm.")
        assertThat(body).contains("https://board.example.com/confirm")
    }

    @Test
    fun `the sender is asked twice before the caller hears about a failure`() {
        smtp.stop()

        val response = post(
            mapOf(
                "to" to "resident@example.com",
                "subject" to "Nobody is listening",
                "text" to "This one cannot be delivered.",
            ),
        )

        assertThat(response.statusCode.value()).isEqualTo(502)
        assertThat(json(response)["code"].asText()).isEqualTo("delivery_failed")
    }
}
