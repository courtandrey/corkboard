package app.corkboard.scopes

import app.corkboard.auth.SessionAuthentication
import app.corkboard.common.ApiException
import app.corkboard.common.ProblemCode
import app.corkboard.events.BBox
import app.corkboard.events.Bounds
import app.corkboard.events.CreateEventRequest
import app.corkboard.events.EventDetail
import app.corkboard.events.EventService
import app.corkboard.events.RenewRequest
import app.corkboard.events.UpdateEventRequest
import app.corkboard.events.ViewportQuery
import app.corkboard.events.ViewportResponse
import app.corkboard.meta.EventType
import jakarta.validation.Valid
import java.security.Principal
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/boards/{ownerId}/events")
@Validated
class BoardController(
    private val events: EventService,
    private val viewport: ViewportQuery,
    private val scopes: ScopeService,
) {

    @GetMapping
    fun list(
        @PathVariable ownerId: UUID,
        @RequestParam @BBox bbox: String,
        @RequestParam(defaultValue = "13") zoom: Int,
        @RequestParam(required = false) types: String?,
        @RequestParam(required = false) tags: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "60") limit: Int,
        @RequestParam(defaultValue = "true") clustered: Boolean,
        principal: Principal?,
    ): ViewportResponse {
        val viewer = viewerId(principal)
        scopes.requireBoardReadable(ownerId, viewer)
        val boardId = scopes.boardOf(ownerId)
            ?: return ViewportResponse(emptyList(), emptyList(), 0)

        return viewport.run(
            ViewportQuery.Params(
                scopeIds = listOf(boardId),
                bounds = Bounds.parse(bbox)!!,
                zoom = zoom.coerceIn(0, 22),
                types = types?.let(::parseTypes),
                tagSlugs = tags?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() },
                applyable = null,
                q = q,
                viewerId = viewer,
                limit = limit.coerceIn(1, 100),
                clustered = clustered,
            ),
        )
    }

    @GetMapping("/{id}")
    fun detail(
        @PathVariable ownerId: UUID,
        @PathVariable id: UUID,
        principal: Principal?,
    ): EventDetail {
        val viewer = viewerId(principal)
        scopes.requireBoardReadable(ownerId, viewer)
        return events.detail(id, viewer, board(ownerId))
    }

    @PreAuthorize("hasAuthority('EVENT_CREATE')")
    @PostMapping
    fun create(
        @PathVariable ownerId: UUID,
        @Valid @RequestBody req: CreateEventRequest,
        auth: SessionAuthentication,
    ): ResponseEntity<EventDetail> {
        scopes.requireBoardOwner(ownerId, auth.user.userId)
        val boardId = scopes.ensureBoardOf(ownerId)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(events.create(auth.user.userId, boardId, req))
    }

    @PreAuthorize("hasAuthority('EVENT_CREATE')")
    @PatchMapping("/{id}")
    fun update(
        @PathVariable ownerId: UUID,
        @PathVariable id: UUID,
        @Valid @RequestBody req: UpdateEventRequest,
        auth: SessionAuthentication,
    ): EventDetail {
        scopes.requireBoardOwner(ownerId, auth.user.userId)
        return events.update(id, auth.user.userId, board(ownerId), req)
    }

    @PreAuthorize("hasAuthority('EVENT_CREATE')")
    @DeleteMapping("/{id}")
    fun remove(
        @PathVariable ownerId: UUID,
        @PathVariable id: UUID,
        auth: SessionAuthentication,
    ): ResponseEntity<Void> {
        scopes.requireBoardOwner(ownerId, auth.user.userId)
        events.remove(id, auth.user.userId, board(ownerId))
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("hasAuthority('EVENT_CREATE')")
    @PostMapping("/{id}/resolve")
    fun resolve(
        @PathVariable ownerId: UUID,
        @PathVariable id: UUID,
        auth: SessionAuthentication,
    ): EventDetail {
        scopes.requireBoardOwner(ownerId, auth.user.userId)
        return events.resolve(id, auth.user.userId, board(ownerId))
    }

    @PreAuthorize("hasAuthority('EVENT_CREATE')")
    @PostMapping("/{id}/renew")
    fun renew(
        @PathVariable ownerId: UUID,
        @PathVariable id: UUID,
        @Valid @RequestBody req: RenewRequest,
        auth: SessionAuthentication,
    ): EventDetail {
        scopes.requireBoardOwner(ownerId, auth.user.userId)
        return events.renew(id, auth.user.userId, board(ownerId), req.expiresAt)
    }

    private fun board(ownerId: UUID): UUID =
        scopes.boardOf(ownerId) ?: throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)

    private fun viewerId(principal: Principal?): UUID? =
        (principal as? SessionAuthentication)?.user?.userId

    private fun parseTypes(csv: String): List<EventType> =
        csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map {
            EventType.fromKey(it)
                ?: throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.VALIDATION_FAILED)
        }
}
