package app.corkboard.places

import app.corkboard.common.CorkboardProperties
import java.net.http.HttpClient
import java.time.Duration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient

@Configuration
class PlacesConfig {

    @Bean
    fun placeSearch(props: CorkboardProperties, builder: RestClient.Builder): PlaceSearch {
        val geocoder = props.geocoder
        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(geocoder.connectTimeoutMillis))
            .build()
        val factory = JdkClientHttpRequestFactory(client).apply {
            setReadTimeout(Duration.ofMillis(geocoder.readTimeoutMillis))
        }
        val http = builder
            .baseUrl(geocoder.url.ifBlank { "http://geocoder.invalid" })
            .requestFactory(factory)
            .build()
        return PhotonPlaceSearch(http, geocoder.language)
    }
}
