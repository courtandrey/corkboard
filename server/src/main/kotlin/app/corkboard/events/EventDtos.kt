package app.corkboard.events

import app.corkboard.meta.EventType
import app.corkboard.scopes.ScopeKind
import com.fasterxml.jackson.annotation.JsonValue
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

enum class EventStatus(@get:JsonValue val key: String) {
    ACTIVE("active"),
    RESOLVED("resolved"),
    EXPIRED("expired"),
    REMOVED("removed"),
    TAKEN_DOWN("taken_down"),
    UNDER_REVIEW("under_review");

    companion object {
        fun fromDb(literal: String): EventStatus = entries.first { it.key == literal }
    }
}

data class LatLng(
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    val lng: Double,
    @field:DecimalMin("-85.0") @field:DecimalMax("85.0")
    val lat: Double,
)

data class CreateEventRequest(
    val type: EventType,
    @field:Size(min = 3, max = 120)
    val title: String,
    @field:Size(min = 1, max = 4000)
    val body: String,
    @field:Valid
    val location: LatLng,
    val applyable: Boolean,
    @field:Future
    val expiresAt: Instant? = null,
    @field:Size(max = 5)
    val tags: List<String> = emptyList(),
)

data class UpdateEventRequest(
    val type: EventType? = null,
    @field:Size(min = 3, max = 120)
    val title: String? = null,
    @field:Size(min = 1, max = 4000)
    val body: String? = null,
    @field:Valid
    val location: LatLng? = null,
    val applyable: Boolean? = null,
    @field:Future
    val expiresAt: Instant? = null,
    val neverExpires: Boolean? = null,
    @field:Size(max = 5)
    val tags: List<String>? = null,
)

data class EventPin(
    val id: UUID,
    val type: EventType,
    val title: String,
    val location: LatLng,
    val applyable: Boolean,
    val score: Int,
    val applicationCount: Int,
    val expiresAt: Instant?,
    val createdAt: Instant,
)

data class ClusterBounds(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
)

data class ClusterPin(
    val count: Int,
    val location: LatLng,
    val bounds: ClusterBounds,
)

data class ViewportResponse(
    val items: List<EventPin>,
    val clusters: List<ClusterPin>,
    val total: Int,
)

data class TagRef(
    val name: String,
    val slug: String,
)

data class EventSnippet(
    val id: UUID,
    val title: String,
    val status: EventStatus,
)

data class AuthorCard(
    val displayName: String,
    val avatarSeed: String,
    val memberSince: Instant,
)

data class ViewerState(
    val voted: Boolean,
    val hidden: Boolean,
    val applied: Boolean,
    val isAuthor: Boolean,
)

data class RenewRequest(
    @field:Future
    val expiresAt: Instant,
)

data class MyEventItem(
    val id: UUID,
    val scope: ScopeKind,
    val boardOwnerId: UUID?,
    val type: EventType,
    val status: EventStatus,
    val title: String,
    val location: LatLng,
    val applyable: Boolean,
    val score: Int,
    val applicationCount: Int,
    val expiresAt: Instant?,
    val resolvedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class MyEventsResponse(
    val items: List<MyEventItem>,
    val nextCursor: String?,
)

data class EventDetail(
    val id: UUID,
    val scope: ScopeKind,
    val boardOwnerId: UUID?,
    val type: EventType,
    val status: EventStatus,
    val title: String,
    val body: String,
    val location: LatLng,
    val applyable: Boolean,
    val score: Int,
    val applicationCount: Int,
    val tags: List<TagRef>,
    val author: AuthorCard,
    val viewerState: ViewerState,
    val expiresAt: Instant?,
    val resolvedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
