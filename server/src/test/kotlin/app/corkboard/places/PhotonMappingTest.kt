package app.corkboard.places

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PhotonMappingTest {

    private val mapper = ObjectMapper().registerKotlinModule()

    private fun suggestions(json: String): List<PlaceSuggestion> =
        PhotonPlaceSearch.suggestions(mapper.readValue(json, PhotonResponse::class.java).features)

    @Test
    fun `a named place keeps its name and reads its context outwards`() {
        val items = suggestions(
            """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","geometry":{"type":"Point","coordinates":[-73.9877,40.7505]},
               "properties":{"osm_id":41010,"osm_type":"N","name":"Herald Square",
                 "district":"Manhattan","city":"New York","state":"New York","country":"United States",
                 "extent":[-73.99,40.76,-73.98,40.74]}}]}
            """,
        )

        assertThat(items).hasSize(1)
        assertThat(items[0].id).isEqualTo("N41010")
        assertThat(items[0].name).isEqualTo("Herald Square")
        assertThat(items[0].context).isEqualTo("Manhattan, New York, United States")
        assertThat(items[0].location.lat).isEqualTo(40.7505)
    }

    @Test
    fun `photon's extent is read as west-north-east-south, not as a bbox`() {
        val bounds = suggestions(
            """
            {"features":[{"geometry":{"coordinates":[4.47,51.92]},
             "properties":{"name":"Rotterdam","extent":[4.35,52.00,4.60,51.85]}}]}
            """,
        ).single().bounds!!

        assertThat(bounds.west).isEqualTo(4.35)
        assertThat(bounds.north).isEqualTo(52.00)
        assertThat(bounds.east).isEqualTo(4.60)
        assertThat(bounds.south).isEqualTo(51.85)
    }

    @Test
    fun `a house has no name, so the street line becomes one`() {
        val item = suggestions(
            """
            {"features":[{"geometry":{"coordinates":[-73.9857,40.7484]},
             "properties":{"osm_id":1,"osm_type":"W","housenumber":"350","street":"5th Avenue",
               "city":"New York","country":"United States"}}]}
            """,
        ).single()

        assertThat(item.name).isEqualTo("350 5th Avenue")
        assertThat(item.context).isEqualTo("New York, United States")
    }

    @Test
    fun `a repeated administrative name is said once`() {
        val item = suggestions(
            """
            {"features":[{"geometry":{"coordinates":[-73.9,40.7]},
             "properties":{"name":"New York","city":"New York","state":"New York","country":"United States"}}]}
            """,
        ).single()

        assertThat(item.context).isEqualTo("United States")
    }

    @Test
    fun `features without a point or a name are dropped`() {
        val items = suggestions(
            """
            {"features":[
              {"properties":{"osm_id":7,"osm_type":"N","name":"No geometry"}},
              {"geometry":{"coordinates":[1.0]},"properties":{"name":"Half a point"}},
              {"geometry":{"coordinates":[1.0,2.0]},"properties":{"osm_id":9,"osm_type":"N"}},
              {"geometry":{"coordinates":[1.0,2.0]},"properties":{"osm_id":8,"osm_type":"N","name":"Kept"}}]}
            """,
        )

        assertThat(items.map { it.name }).containsExactly("Kept")
    }

    @Test
    fun `a station's every door reads the same, so the list shows it once`() {
        val items = suggestions(
            """
            {"features":[
              {"geometry":{"coordinates":[4.469,51.925]},
               "properties":{"osm_id":1,"osm_type":"N","name":"Rotterdam Centraal",
                 "street":"Provenierstunnel","district":"Centrum","city":"Rotterdam"}},
              {"geometry":{"coordinates":[4.468,51.925]},
               "properties":{"osm_id":2,"osm_type":"N","name":"Rotterdam Centraal",
                 "street":"Provenierstunnel","district":"Centrum","city":"Rotterdam"}},
              {"geometry":{"coordinates":[4.467,51.926]},
               "properties":{"osm_id":3,"osm_type":"N","name":"Rotterdam Centraal",
                 "street":"Stationssingel","district":"Noord","city":"Rotterdam"}}]}
            """,
        )

        assertThat(items.map { it.context })
            .containsExactly("Provenierstunnel, Centrum, Rotterdam", "Stationssingel, Noord, Rotterdam")
    }
}
