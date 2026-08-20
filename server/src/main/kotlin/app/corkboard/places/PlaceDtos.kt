package app.corkboard.places

import app.corkboard.events.LatLng

data class PlaceBounds(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
)

data class PlaceSuggestion(
    val id: String,
    val name: String,
    val context: String?,
    val location: LatLng,
    val bounds: PlaceBounds? = null,
)

data class PlaceSuggestions(val items: List<PlaceSuggestion>)

interface PlaceSearch {
    fun search(q: String, near: LatLng?, limit: Int): List<PlaceSuggestion>
}

class PlaceSearchUnavailableException(cause: Throwable? = null) : RuntimeException(cause)
