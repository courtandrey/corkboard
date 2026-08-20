package app.corkboard.places

import app.corkboard.events.LatLng
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.util.UriBuilder

private const val CONTEXT_PARTS = 3

private const val OVER_ASK = 2
private const val MAX_ASK = 20

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class PhotonResponse(val features: List<PhotonFeature> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class PhotonFeature(
    val geometry: PhotonGeometry? = null,
    val properties: PhotonProperties = PhotonProperties(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class PhotonGeometry(val coordinates: List<Double> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class PhotonProperties(
    @param:JsonProperty("osm_id") val osmId: Long? = null,
    @param:JsonProperty("osm_type") val osmType: String? = null,
    val name: String? = null,
    val housenumber: String? = null,
    val street: String? = null,
    val district: String? = null,
    val city: String? = null,
    val county: String? = null,
    val state: String? = null,
    val country: String? = null,
    val extent: List<Double>? = null,
)

class PhotonPlaceSearch(private val http: RestClient, private val language: String) : PlaceSearch {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun search(q: String, near: LatLng?, limit: Int): List<PlaceSuggestion> {
        val response = try {
            http.get()
                .uri { builder -> uri(builder, q, near, (limit * OVER_ASK).coerceAtMost(MAX_ASK)) }
                .retrieve()
                .body(PhotonResponse::class.java)
        } catch (failed: RestClientException) {
            log.warn("geocoder refused \"{}\": {}", q, failed.message)
            throw PlaceSearchUnavailableException(failed)
        }
        return suggestions(response?.features ?: emptyList()).take(limit)
    }

    private fun uri(builder: UriBuilder, q: String, near: LatLng?, limit: Int) =
        builder.path("/api")
            .queryParam("q", q)
            .queryParam("limit", limit)
            .queryParam("lang", language)
            .apply {
                if (near != null) {
                    queryParam("lat", near.lat)
                    queryParam("lon", near.lng)
                }
            }
            .build()

    internal companion object {

        fun suggestions(features: List<PhotonFeature>): List<PlaceSuggestion> =
            features.mapIndexedNotNull(::suggestion).distinctBy { it.name to it.context }

        private fun suggestion(index: Int, feature: PhotonFeature): PlaceSuggestion? {
            val coordinates = feature.geometry?.coordinates ?: return null
            if (coordinates.size < 2) return null
            val p = feature.properties
            val name = name(p) ?: return null
            return PlaceSuggestion(
                id = p.osmId?.let { "${p.osmType ?: "?"}$it" } ?: "photon-$index",
                name = name,
                context = context(p, name),
                location = LatLng(lng = coordinates[0], lat = coordinates[1]),
                bounds = bounds(p.extent),
            )
        }

        private fun name(p: PhotonProperties): String? =
            p.name?.trim()?.takeIf { it.isNotEmpty() }
                ?: streetLine(p)
                ?: listOfNotNull(p.city, p.state, p.country).firstOrNull { it.isNotBlank() }

        private fun streetLine(p: PhotonProperties): String? {
            val street = p.street?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val number = p.housenumber?.trim()?.takeIf { it.isNotEmpty() } ?: return street
            return "$number $street"
        }

        private fun context(p: PhotonProperties, name: String): String? =
            listOfNotNull(streetLine(p), p.district, p.city, p.county, p.state, p.country)
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != name }
                .distinct()
                .take(CONTEXT_PARTS)
                .joinToString(", ")
                .takeIf { it.isNotEmpty() }

        private fun bounds(extent: List<Double>?): PlaceBounds? {
            if (extent == null || extent.size < 4) return null
            val (west, north, east, south) = extent
            return PlaceBounds(west = west, south = south, east = east, north = north)
        }
    }
}
