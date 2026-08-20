package app.corkboard.places

import app.corkboard.common.ApiException
import app.corkboard.common.CorkboardProperties
import app.corkboard.common.ProblemCode
import app.corkboard.common.RateLimiter
import app.corkboard.events.LatLng
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private const val MIN_QUERY = 3
private const val MAX_QUERY = 120
private const val MAX_LIMIT = 10

@RestController
@RequestMapping("/api/v1/places")
class PlacesController(
    private val places: PlaceSearch,
    private val props: CorkboardProperties,
) {

    private val limiter = RateLimiter(props.geocoder.perIpPerMinute)

    @GetMapping
    fun search(
        @RequestParam q: String,
        @RequestParam(required = false) near: String?,
        @RequestParam(defaultValue = "6") limit: Int,
        request: HttpServletRequest,
    ): PlaceSuggestions {
        if (!props.geocoder.enabled) throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, ProblemCode.PLACE_SEARCH_OFF)
        val text = q.trim()
        if (text.length !in MIN_QUERY..MAX_QUERY) {
            throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.VALIDATION_FAILED)
        }
        if (!limiter.tryConsume(request.remoteAddr)) {
            throw ApiException(HttpStatus.TOO_MANY_REQUESTS, ProblemCode.RATE_LIMITED)
        }
        val items = try {
            places.search(text, parseNear(near), limit.coerceIn(1, MAX_LIMIT))
        } catch (unavailable: PlaceSearchUnavailableException) {
            throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, ProblemCode.PLACE_SEARCH_OFF)
        }
        return PlaceSuggestions(items)
    }

    private fun parseNear(near: String?): LatLng? {
        val parts = near?.split(',')?.mapNotNull { it.trim().toDoubleOrNull() } ?: return null
        if (parts.size != 2) return null
        val (lng, lat) = parts
        return if (lng in -180.0..180.0 && lat in -85.0..85.0) LatLng(lng = lng, lat = lat) else null
    }
}
