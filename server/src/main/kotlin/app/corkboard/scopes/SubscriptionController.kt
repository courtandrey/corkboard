package app.corkboard.scopes

import app.corkboard.auth.SessionAuthentication
import app.corkboard.common.ApiException
import app.corkboard.common.ProblemCode
import app.corkboard.connections.ConnectionService
import app.corkboard.events.BBox
import app.corkboard.events.Bounds
import app.corkboard.events.EventDetail
import app.corkboard.events.EventService
import app.corkboard.events.ViewportQuery
import app.corkboard.events.ViewportResponse
import app.corkboard.meta.EventType
import jakarta.validation.Valid
import java.time.Instant
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class SubscriptionItem(
    val ownerId: UUID,
    val handle: String,
    val displayName: String,
    val avatarSeed: String,
    val memberSince: Instant,
)

data class SubscriptionsResponse(
    val following: List<SubscriptionItem>,
    val viewers: List<SubscriptionItem>,
)

data class ShareBoardRequest(val userId: UUID)

@RestController
@RequestMapping("/api/v1/subscriptions")
@Validated
class SubscriptionController(
    private val events: EventService,
    private val viewport: ViewportQuery,
    private val scopes: ScopeService,
    private val connections: ConnectionService,
) {

    @GetMapping
    fun mine(auth: SessionAuthentication): SubscriptionsResponse =
        SubscriptionsResponse(
            following = scopes.boardsReadableBy(auth.user.userId).map(::toItem),
            viewers = scopes.viewersOf(auth.user.userId).map(::toItem),
        )

    @GetMapping("/events")
    fun list(
        @RequestParam @BBox bbox: String,
        @RequestParam(defaultValue = "13") zoom: Int,
        @RequestParam(required = false) types: String?,
        @RequestParam(required = false) tags: String?,
        @RequestParam(required = false) owners: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "60") limit: Int,
        @RequestParam(defaultValue = "true") clustered: Boolean,
        auth: SessionAuthentication,
    ): ViewportResponse {
        val boards = readable(auth.user.userId, owners)
        if (boards.isEmpty()) return ViewportResponse(emptyList(), emptyList(), 0)

        return viewport.run(
            ViewportQuery.Params(
                scopeIds = boards,
                bounds = Bounds.parse(bbox)!!,
                zoom = zoom.coerceIn(0, 22),
                types = types?.let(::parseTypes),
                tagSlugs = tags?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() },
                applyable = null,
                q = q,
                viewerId = auth.user.userId,
                limit = limit.coerceIn(1, 100),
                clustered = clustered,
            ),
        )
    }

    @GetMapping("/events/{id}")
    fun detail(@PathVariable id: UUID, auth: SessionAuthentication): EventDetail =
        events.detailAcross(id, auth.user.userId, readable(auth.user.userId, null))

    @PreAuthorize("hasAuthority('CONNECTION_MANAGE')")
    @PostMapping("/viewers")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun share(auth: SessionAuthentication, @Valid @RequestBody req: ShareBoardRequest) {
        if (!connections.areConnected(auth.user.userId, req.userId)) {
            throw ApiException(HttpStatus.FORBIDDEN, ProblemCode.NOT_CONNECTED)
        }
        scopes.share(auth.user.userId, req.userId)
    }

    @PreAuthorize("hasAuthority('CONNECTION_MANAGE')")
    @DeleteMapping("/viewers/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unshare(auth: SessionAuthentication, @PathVariable userId: UUID) {
        scopes.unshare(auth.user.userId, userId)
    }

    private fun readable(viewerId: UUID, owners: String?): List<UUID> {
        val wanted = owners?.split(',')?.mapNotNull { runCatching { UUID.fromString(it.trim()) }.getOrNull() }
        val boards = scopes.boardsReadableBy(viewerId)
        return boards.filter { wanted.isNullOrEmpty() || it.ownerId in wanted }.map { it.scopeId }
    }

    private fun toItem(subscription: Subscription) = SubscriptionItem(
        ownerId = subscription.ownerId,
        handle = subscription.handle,
        displayName = subscription.displayName,
        avatarSeed = subscription.avatarSeed,
        memberSince = subscription.memberSince,
    )

    private fun parseTypes(raw: String): List<EventType>? =
        raw.split(',').mapNotNull { key -> EventType.entries.firstOrNull { it.key == key.trim() } }
            .takeIf { it.isNotEmpty() }
}
