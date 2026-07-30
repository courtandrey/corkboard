package app.corkboard.notifier.mail

import app.corkboard.notifier.config.NotifierProperties
import app.corkboard.notifier.config.NotifierProperties.Transport
import java.net.http.HttpClient
import java.time.Duration
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.web.client.RestClient

@Configuration
class MailConfig {

    @Bean
    fun emailSender(
        props: NotifierProperties,
        mailSenders: ObjectProvider<JavaMailSender>,
        restClients: ObjectProvider<RestClient.Builder>,
    ): EmailSender =
        when (props.transport) {
            Transport.LOG -> LogEmailSender()
            Transport.SMTP -> {
                val mail = mailSenders.ifAvailable
                    ?: error("NOTIFIER_TRANSPORT=smtp needs an SMTP server — set MAIL_HOST (and MAIL_PORT/MAIL_USERNAME/MAIL_PASSWORD)")
                check(props.from.isNotBlank()) { "NOTIFIER_TRANSPORT=smtp needs a sender address — set MAIL_FROM" }
                SmtpEmailSender(mail, props)
            }
            Transport.RESEND -> {
                check(props.resend.apiKey.isNotBlank()) { "NOTIFIER_TRANSPORT=resend needs an API key — set RESEND_API_KEY" }
                check(props.from.isNotBlank()) { "NOTIFIER_TRANSPORT=resend needs a sender address on a verified domain — set MAIL_FROM" }
                ResendEmailSender(resendClient(props, restClients.getObject()), props)
            }
        }

    private fun resendClient(props: NotifierProperties, builder: RestClient.Builder): RestClient {
        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(props.resend.connectTimeoutMillis))
            .build()
        val factory = JdkClientHttpRequestFactory(client).apply {
            setReadTimeout(Duration.ofMillis(props.resend.readTimeoutMillis))
        }
        return builder
            .baseUrl(props.resend.baseUrl)
            .requestFactory(factory)
            .build()
    }
}
