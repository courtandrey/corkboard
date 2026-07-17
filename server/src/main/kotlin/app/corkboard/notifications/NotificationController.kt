package app.corkboard.notifications

import app.corkboard.auth.SessionAuthentication
import java.util.UUID
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class MarkReadRequest(val ids: List<UUID>? = null)

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(private val notifications: NotificationService) {

    @GetMapping
    fun list(
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "30") limit: Int,
        auth: SessionAuthentication,
    ): NotificationListResponse =
        notifications.list(auth.user.userId, cursor, limit.coerceIn(1, 100))

    @PostMapping("/read")
    fun markRead(
        @RequestBody(required = false) req: MarkReadRequest?,
        auth: SessionAuthentication,
    ): ResponseEntity<Void> {
        notifications.markRead(auth.user.userId, req?.ids)
        return ResponseEntity.noContent().build()
    }
}
