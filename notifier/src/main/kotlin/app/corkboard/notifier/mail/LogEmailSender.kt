package app.corkboard.notifier.mail

import org.slf4j.LoggerFactory

class LogEmailSender : EmailSender {

    private val log = LoggerFactory.getLogger(javaClass)

    override val transport = "log"

    override fun send(email: OutgoingEmail) {
        log.info(
            "email {} would go to {}\n  subject: {}\n{}",
            email.id,
            email.to,
            email.subject,
            email.text.prependIndent("  "),
        )
    }
}
