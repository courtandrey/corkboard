package app.corkboard.notifier.notifications

import app.corkboard.notifications.avro.NotificationRequested
import app.corkboard.notifications.avro.NotificationType
import app.corkboard.notifier.kafka.InvalidNotificationException
import app.corkboard.notifier.mail.EmailDispatcher
import app.corkboard.notifier.mail.EmailRequest
import app.corkboard.notifier.mail.EmailService
import app.corkboard.notifier.templates.EmailTemplates
import app.corkboard.notifier.templates.MissingTemplateException
import app.corkboard.notifier.templates.MissingVariableException
import app.corkboard.notifier.templates.TemplateRenderer
import java.util.concurrent.CompletableFuture
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

data class Handled(val messageId: String?, val duplicate: Boolean)

@Component
class NotificationHandler(
    private val templates: EmailTemplates,
    private val renderer: TemplateRenderer,
    private val dispatcher: EmailDispatcher,
    private val emails: EmailService,
    private val sent: SentNotifications,
    private val transactions: TransactionTemplate,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun handle(event: NotificationRequested): CompletableFuture<Handled> {
        val type = event.type
        if (type == NotificationType.UNKNOWN) {
            throw InvalidNotificationException("notification type is unknown to this build")
        }
        val idempotencyId = event.idempotencyId.toString()
        val request = render(event, type, idempotencyId)
        emails.validate(request)

        return dispatcher.dispatch { deliver(idempotencyId, type.name, request) }
    }

    private fun deliver(idempotencyId: String, type: String, request: EmailRequest): Handled {
        if (emails.deduplicates) return Handled(emails.send(request), duplicate = false)

        return transactions.execute {
            if (!sent.claim(idempotencyId, type)) {
                log.info("skipping {} — already sent", idempotencyId)
                return@execute Handled(null, duplicate = true)
            }
            Handled(emails.send(request), duplicate = false)
        }!!
    }

    private fun render(event: NotificationRequested, type: NotificationType, idempotencyId: String): EmailRequest {
        val rendered = try {
            renderer.render(
                templates.latest(type.name),
                event.variables.entries.associate { it.key.toString() to it.value.toString() },
            )
        } catch (unrenderable: MissingTemplateException) {
            throw InvalidNotificationException(unrenderable.message ?: "no template", unrenderable)
        } catch (unrenderable: MissingVariableException) {
            throw InvalidNotificationException(unrenderable.message ?: "missing variables", unrenderable)
        }

        return EmailRequest(
            to = event.recipient.email.toString(),
            toName = event.recipient.name?.toString(),
            subject = rendered.subject,
            text = rendered.text,
            html = rendered.html,
            key = idempotencyId,
        )
    }
}
