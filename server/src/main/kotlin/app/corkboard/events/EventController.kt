package app.corkboard.events

import app.corkboard.auth.SessionAuthentication
import app.corkboard.common.ApiException
import app.corkboard.common.ProblemCode
import app.corkboard.meta.EventType
import app.corkboard.scopes.ScopeService
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
@RequestMapping("/api/v1/events")
@Validated
class EventController(
    private val events: EventService,
    private val viewport: ViewportQuery,
    private val scopes: ScopeService,
) {

    @GetMapping
    fun list(
        @RequestParam @BBox bbox: String,
        @RequestParam(defaultValue = "13") zoom: Int,
        @RequestParam(required = false) types: String?,
        @RequestParam(required = false) tags: String?,
        @RequestParam(required = false) applyable: Boolean?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "60") limit: Int,
        @RequestParam(defaultValue = "true") clustered: Boolean,
        principal: Principal?,
    ): ViewportResponse {
        val viewer = viewerId(principal)
        return viewport.run(
            ViewportQuery.Params(
                scopeIds = listOf(scopes.globalId),
                bounds = Bounds.parse(bbox)!!,
                zoom = zoom.coerceIn(0, 22),
                types = types?.let(::parseTypes),
                tagSlugs = tags?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() },
                applyable = applyable,
                q = q,
                viewerId = viewer,
                limit = limit.coerceIn(1, 100),
                clustered = clustered,
            ),
        )
    }

    @GetMapping("/{id}")
    fun detail(@PathVariable id: UUID, principal: Principal?): EventDetail =
        events.detail(id, viewerId(principal), scopes.globalId)

    @PreAuthorize("hasAuthority('EVENT_CREATE')")
    @PostMapping
    fun create(
        @Valid @RequestBody req: CreateEventRequest,
        auth: SessionAuthentication,
    ): ResponseEntity<EventDetail> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(events.create(auth.user.userId, scopes.globalId, req))

    @PreAuthorize("hasAuthority('EVENT_CREATE')")
    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody req: UpdateEventRequest,
        auth: SessionAuthentication,
    ): EventDetail = events.update(id, auth.user.userId, scopes.globalId, req)

    @PreAuthorize("hasAuthority('EVENT_CREATE')")
    @DeleteMapping("/{id}")
    fun remove(@PathVariable id: UUID, auth: SessionAuthentication): ResponseEntity<Void> {
        events.remove(id, auth.user.userId, scopes.globalId)
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("hasAuthority('EVENT_CREATE')")
    @PostMapping("/{id}/resolve")
    fun resolve(@PathVariable id: UUID, auth: SessionAuthentication): EventDetail =
        events.resolve(id, auth.user.userId, scopes.globalId)

    @PreAuthorize("hasAuthority('EVENT_CREATE')")
    @PostMapping("/{id}/renew")
    fun renew(
        @PathVariable id: UUID,
        @Valid @RequestBody req: RenewRequest,
        auth: SessionAuthentication,
    ): EventDetail = events.renew(id, auth.user.userId, scopes.globalId, req.expiresAt)

    private fun viewerId(principal: Principal?): UUID? =
        (principal as? SessionAuthentication)?.user?.userId

    private fun parseTypes(csv: String): List<EventType> =
        csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map {
            EventType.fromKey(it)
                ?: throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.VALIDATION_FAILED)
        }
}
