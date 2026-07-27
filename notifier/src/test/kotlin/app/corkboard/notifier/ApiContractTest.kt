package app.corkboard.notifier

import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.ServerSetupTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ApiContractTest : NotifierTestBase() {

    @RegisterExtension
    val smtp: GreenMailExtension = GreenMailExtension(ServerSetupTest.SMTP)

    private val validBody = mapOf(
        "to" to "resident@example.com",
        "subject" to "Hello",
        "text" to "Hello there.",
    )

    @Test
    fun `sending needs the api key`() {
        val missing = post(validBody, apiKey = null)
        assertThat(missing.statusCode.value()).isEqualTo(401)
        assertThat(json(missing)["code"].asText()).isEqualTo("unauthenticated")

        val wrong = post(validBody, apiKey = "not-the-key")
        assertThat(wrong.statusCode.value()).isEqualTo(401)

        assertThat(smtp.receivedMessages).isEmpty()
    }

    @Test
    fun `health is open and names the transport`() {
        val response = rest.getForEntity("/api/v1/health", String::class.java)
        assertThat(response.statusCode.value()).isEqualTo(200)
        assertThat(json(response)["status"].asText()).isEqualTo("ok")
        assertThat(json(response)["transport"].asText()).isEqualTo("smtp")
    }

    @Test
    fun `a malformed recipient is rejected before anything is sent`() {
        val response = post(validBody + ("to" to "not-an-address"))
        assertThat(response.statusCode.value()).isEqualTo(422)
        val body = json(response)
        assertThat(body["code"].asText()).isEqualTo("validation_failed")
        assertThat(body["fields"]["to"].asText()).isNotBlank()
        assertThat(smtp.receivedMessages).isEmpty()
    }

    @Test
    fun `an oversized subject is rejected`() {
        val response = post(validBody + ("subject" to "x".repeat(201)))
        assertThat(response.statusCode.value()).isEqualTo(422)
        assertThat(json(response)["code"].asText()).isEqualTo("validation_failed")
        assertThat(smtp.receivedMessages).isEmpty()
    }
}
