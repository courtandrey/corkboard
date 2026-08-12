package app.corkboard.features

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class FeaturesResponse(val flags: Map<String, Boolean>)

@RestController
@RequestMapping("/api/v1/features")
class FeatureFlagController(private val flags: FeatureFlagService) {

    @GetMapping
    fun features(): FeaturesResponse = FeaturesResponse(flags.enabledByKey())
}
