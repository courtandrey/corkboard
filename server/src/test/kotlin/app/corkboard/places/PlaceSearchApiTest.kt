package app.corkboard.places

import app.corkboard.ApiTestBase
import app.corkboard.events.LatLng
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary

class StubPlaceSearch : PlaceSearch {

    var asked: Triple<String, LatLng?, Int>? = null
    var refuse = false

    override fun search(q: String, near: LatLng?, limit: Int): List<PlaceSuggestion> {
        asked = Triple(q, near, limit)
        if (refuse) throw PlaceSearchUnavailableException()
        return listOf(
            PlaceSuggestion(
                id = "N1",
                name = "Herald Square",
                context = "Manhattan, New York",
                location = LatLng(lng = -73.9877, lat = 40.7505),
                bounds = PlaceBounds(west = -73.99, south = 40.74, east = -73.98, north = 40.76),
            ),
        )
    }
}

@TestConfiguration
class StubPlaceSearchConfig {
    @Bean
    @Primary
    fun stubPlaceSearch(): PlaceSearch = StubPlaceSearch()
}

@Import(StubPlaceSearchConfig::class)
class PlaceSearchApiTest : ApiTestBase() {

    @Autowired
    lateinit var places: PlaceSearch

    @Test
    fun `a visitor can look up an address without signing in`() {
        val body = json(getJson("/api/v1/places?q=herald+square&near=-73.98,40.75"))
        val first = body["items"][0]

        assertThat(first["name"].asText()).isEqualTo("Herald Square")
        assertThat(first["context"].asText()).isEqualTo("Manhattan, New York")
        assertThat(first["location"]["lng"].asDouble()).isEqualTo(-73.9877)
        assertThat(first["bounds"]["north"].asDouble()).isEqualTo(40.76)
    }

    @Test
    fun `the map's centre is passed on, so results are local first`() {
        getJson("/api/v1/places?q=main+street&near=4.4777,51.9244")

        val stub = stub()
        assertThat(stub.asked?.first).isEqualTo("main street")
        assertThat(stub.asked?.second).isEqualTo(LatLng(lng = 4.4777, lat = 51.9244))
    }

    @Test
    fun `a nonsense near is ignored rather than refused`() {
        val res = getJson("/api/v1/places?q=herald+square&near=over-there")

        assertThat(res.statusCode.value()).isEqualTo(200)
        assertThat(stub().asked?.second).isNull()
    }

    @Test
    fun `two letters are not a search`() {
        val res = getJson("/api/v1/places?q=he")

        assertThat(res.statusCode.value()).isEqualTo(422)
        assertThat(json(res)["code"].asText()).isEqualTo("validation_failed")
    }

    @Test
    fun `a geocoder that will not answer is a 503, not a 500`() {
        val stub = stub()
        stub.refuse = true
        try {
            val res = getJson("/api/v1/places?q=herald+square")

            assertThat(res.statusCode.value()).isEqualTo(503)
            assertThat(json(res)["code"].asText()).isEqualTo("place_search_off")
        } finally {
            stub.refuse = false
        }
    }

    @Test
    fun `meta says address search is on`() {
        assertThat(json(getJson("/api/v1/meta"))["placeSearch"].asBoolean()).isTrue()
    }

    private fun stub(): StubPlaceSearch = places as StubPlaceSearch
}
