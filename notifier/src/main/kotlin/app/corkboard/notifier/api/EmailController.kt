package app.corkboard.notifier.api

import app.corkboard.notifier.mail.EmailDispatcher
import app.corkboard.notifier.mail.EmailRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class SendEmailRequest(
    @field:NotBlank @field:Email val to: String = "",
    @field:NotBlank @field:Size(max = 200) val subject: String = "",
    @field:NotBlank val text: String = "",
    val html: String? = null,
    @field:Email val replyTo: String? = null,
    val key: String? = null,
)

data class SendEmailResponse(val id: String, val transport: String, val key: String?)

@RestController
@RequestMapping("/api/v1/emails")
class EmailController(private val dispatcher: EmailDispatcher) {

    @PostMapping
    fun send(@Valid @RequestBody req: SendEmailRequest): ResponseEntity<SendEmailResponse> {
        val id = dispatcher.dispatchNow(
            EmailRequest(
                to = req.to.trim(),
                subject = req.subject.trim(),
                text = req.text,
                html = req.html,
                replyTo = req.replyTo?.trim(),
                key = req.key,
            ),
        )
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(SendEmailResponse(id, dispatcher.transport, req.key))
    }
}
