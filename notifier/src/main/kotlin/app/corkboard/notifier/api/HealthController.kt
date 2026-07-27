package app.corkboard.notifier.api

import app.corkboard.notifier.mail.EmailService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

data class HealthResponse(val status: String, val transport: String)

@RestController
class HealthController(private val emails: EmailService) {

    @GetMapping("/api/v1/health")
    fun health(): HealthResponse = HealthResponse("ok", emails.transport)
}
