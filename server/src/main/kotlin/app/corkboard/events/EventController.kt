package app.corkboard.events

import app.corkboard.auth.SessionAuthentication
import app.corkboard.common.ApiException
import app.corkboard.common.ProblemCode
import app.corkboard.meta.EventType
import jakarta.validation.Valid
import java.security.Principal
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
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
        principal: Principal?,
    ): ViewportResponse = viewport.run(
        ViewportQuery.Params(
            bounds = Bounds.parse(bbox)!!,
            zoom = zoom.coerceIn(1, 22),
            types = types?.let(::parseTypes),
            tagSlugs = tags?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() },
            applyable = applyable,
            q = q,
            viewerId = viewerId(principal),
            limit = limit.coerceIn(1, 100),
        )
    )

    @GetMapping("/{id}")
    fun detail(@PathVariable id: UUID, principal: Principal?): EventDetail =
        events.detail(id, viewerId(principal))

    @PostMapping
    fun create(
        @Valid @RequestBody req: CreateEventRequest,
        auth: SessionAuthentication,
    ): ResponseEntity<EventDetail> =
        ResponseEntity.status(HttpStatus.CREATED).body(events.create(auth.user.userId, req))

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody req: UpdateEventRequest,
        auth: SessionAuthentication,
    ): EventDetail = events.update(id, auth.user.userId, req)

    private fun viewerId(principal: Principal?): UUID? =
        (principal as? SessionAuthentication)?.user?.userId

    private fun parseTypes(csv: String): List<EventType> =
        csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map {
            EventType.fromKey(it)
                ?: throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.VALIDATION_FAILED)
        }
}
