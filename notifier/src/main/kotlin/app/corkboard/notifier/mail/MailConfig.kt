package app.corkboard.notifier.mail

import app.corkboard.notifier.config.NotifierProperties
import app.corkboard.notifier.config.NotifierProperties.Transport
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.javamail.JavaMailSender

@Configuration
class MailConfig {

    @Bean
    fun emailSender(
        props: NotifierProperties,
        mailSenders: ObjectProvider<JavaMailSender>,
    ): EmailSender =
        when (props.transport) {
            Transport.LOG -> LogEmailSender()
            Transport.SMTP -> {
                val mail = mailSenders.ifAvailable
                    ?: error("NOTIFIER_TRANSPORT=smtp needs an SMTP server — set MAIL_HOST (and MAIL_PORT/MAIL_USERNAME/MAIL_PASSWORD)")
                check(props.from.isNotBlank()) { "NOTIFIER_TRANSPORT=smtp needs a sender address — set MAIL_FROM" }
                SmtpEmailSender(mail, props)
            }
        }
}
