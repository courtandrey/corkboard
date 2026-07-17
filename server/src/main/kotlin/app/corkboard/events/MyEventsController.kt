package app.corkboard.events

import app.corkboard.auth.SessionAuthentication
import app.corkboard.common.ApiException
import app.corkboard.common.ProblemCode
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/me/events")
class MyEventsController(private val events: EventService) {

    @GetMapping
    fun list(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "50") limit: Int,
        auth: SessionAuthentication,
    ): MyEventsResponse {
        val parsed = status?.let { key ->
            EventStatus.entries.firstOrNull { it.key == key }
                ?: throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.VALIDATION_FAILED)
        }
        return events.myEvents(auth.user.userId, parsed, cursor, limit.coerceIn(1, 200))
    }
}
