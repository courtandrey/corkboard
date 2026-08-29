package app.corkboard.messaging

import app.corkboard.events.AuthorCard
import app.corkboard.events.EventSnippet
import com.fasterxml.jackson.annotation.JsonValue
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

enum class ApplicationStatus(@get:JsonValue val key: String) {
    PENDING("pending"),
    ACCEPTED("accepted"),
    DECLINED("declined"),
    WITHDRAWN("withdrawn");

    companion object {
        fun fromDb(literal: String): ApplicationStatus = entries.first { it.key == literal }
    }
}

data class MessageResponse(
    val id: UUID,
    val conversationId: UUID,
    val senderId: UUID,
    val body: String,
    val event: EventSnippet?,
    val createdAt: Instant,
    val readAt: Instant?,
)

data class SendMessageRequest(
    @field:Size(min = 1, max = 2000)
    val body: String,
)

data class MessageListResponse(
    val items: List<MessageResponse>,
    val nextCursor: String?,
)

data class ConversationSummary(
    val id: UUID,
    val otherParty: AuthorCard,
    val lastMessageAt: Instant,
    val lastMessageBody: String?,
    val unreadCount: Int,
)

data class ConversationRef(val id: UUID)

data class ConversationListResponse(
    val items: List<ConversationSummary>,
    val nextCursor: String?,
)

data class MessageCreated(
    val recipientId: UUID,
    val conversationId: UUID,
    val message: MessageResponse,
)

data class ConversationRead(
    val recipientId: UUID,
    val conversationId: UUID,
)
