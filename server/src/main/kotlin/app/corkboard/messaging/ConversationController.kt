package app.corkboard.messaging

import app.corkboard.auth.SessionAuthentication
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/conversations")
class ConversationController(private val conversations: ConversationService) {

    @GetMapping
    fun list(
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "30") limit: Int,
        auth: SessionAuthentication,
    ): ConversationListResponse =
        conversations.list(auth.user.userId, cursor, limit.coerceIn(1, 100))

    /** The thread with someone you know — opened here rather than by answering a note. */
    @PreAuthorize("hasAuthority('MESSAGE_SEND')")
    @PostMapping("/with/{userId}")
    fun with(@PathVariable userId: UUID, auth: SessionAuthentication): ConversationRef =
        ConversationRef(conversations.openWith(auth.user.userId, userId))

    @GetMapping("/{id}/messages")
    fun messages(
        @PathVariable id: UUID,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "50") limit: Int,
        auth: SessionAuthentication,
    ): MessageListResponse =
        conversations.messages(id, auth.user.userId, cursor, limit.coerceIn(1, 200))

    @PreAuthorize("hasAuthority('MESSAGE_SEND')")
    @PostMapping("/{id}/messages")
    fun send(
        @PathVariable id: UUID,
        @Valid @RequestBody req: SendMessageRequest,
        auth: SessionAuthentication,
    ): ResponseEntity<MessageResponse> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(conversations.send(id, auth.user.userId, req.body))

    @PostMapping("/{id}/read")
    fun read(@PathVariable id: UUID, auth: SessionAuthentication): ResponseEntity<Void> {
        conversations.markRead(id, auth.user.userId)
        return ResponseEntity.noContent().build()
    }
}
