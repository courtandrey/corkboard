package app.corkboard.applications

import app.corkboard.events.AuthorCard
import app.corkboard.events.EventSnippet
import app.corkboard.messaging.ApplicationStatus
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

enum class ApplicationRole { SENT, RECEIVED }

data class ApplyRequest(
    @field:Size(min = 1, max = 2000)
    val message: String,
)

data class ApplicationResponse(
    val id: UUID,
    val eventId: UUID,
    val status: ApplicationStatus,
    val createdAt: Instant,
)

data class ApplyResponse(
    val application: ApplicationResponse,
    val conversationId: UUID,
)

data class UpdateApplicationRequest(
    val status: ApplicationStatus,
)

data class ApplicationItem(
    val id: UUID,
    val status: ApplicationStatus,
    val message: String?,
    val createdAt: Instant,
    val conversationId: UUID,
    val applicant: AuthorCard? = null,
)

data class ApplicationGroup(
    val event: EventSnippet,
    val applications: List<ApplicationItem>,
)

data class MyApplicationsResponse(
    val items: List<ApplicationGroup>,
)
