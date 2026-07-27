package app.corkboard.notifier.mail

import app.corkboard.notifier.config.NotifierProperties
import jakarta.mail.internet.InternetAddress
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper

class SmtpEmailSender(
    private val mail: JavaMailSender,
    private val props: NotifierProperties,
) : EmailSender {

    override val transport = "smtp"

    override fun send(email: OutgoingEmail) {
        val message = mail.createMimeMessage()
        val helper = MimeMessageHelper(message, email.html != null, Charsets.UTF_8.name())
        helper.setTo(email.to)
        helper.setSubject(email.subject)
        helper.setFrom(InternetAddress(props.sender, props.fromName.ifBlank { null }, Charsets.UTF_8.name()))
        if (email.html != null) helper.setText(email.text, email.html) else helper.setText(email.text)
        (email.replyTo ?: props.replyTo.ifBlank { null })?.let { helper.setReplyTo(it) }
        message.setHeader("X-Corkboard-Message-Id", email.id)
        mail.send(message)
    }
}
