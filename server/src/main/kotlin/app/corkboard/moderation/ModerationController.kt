package app.corkboard.moderation

import app.corkboard.auth.SessionAuthentication
import com.fasterxml.jackson.annotation.JsonValue
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

enum class ReportReason(@get:JsonValue val key: String) {
    SPAM("spam"),
    OFFENSIVE("offensive"),
    SCAM("scam"),
    DANGER("danger"),
    OTHER("other"),
}

data class ReportRequest(
    val reason: ReportReason,
    @field:Size(max = 500)
    val detail: String? = null,
)

@RestController
@RequestMapping("/api/v1/events/{id}")
class ModerationController(
    private val hides: HideService,
    private val reports: ReportService,
) {

    @PostMapping("/hide")
    fun hide(@PathVariable id: UUID, auth: SessionAuthentication): ResponseEntity<Void> {
        hides.hide(id, auth.user.userId)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/hide")
    fun unhide(@PathVariable id: UUID, auth: SessionAuthentication): ResponseEntity<Void> {
        hides.unhide(id, auth.user.userId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/report")
    fun report(
        @PathVariable id: UUID,
        @Valid @RequestBody req: ReportRequest,
        auth: SessionAuthentication,
    ): ResponseEntity<Void> {
        reports.report(id, auth.user.userId, req)
        return ResponseEntity.status(HttpStatus.ACCEPTED).build()
    }
}
