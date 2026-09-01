package app.corkboard.features

import com.fasterxml.jackson.annotation.JsonValue
import org.springframework.http.HttpMethod

enum class FeatureFlag(val defaultEnabled: Boolean) {
    ARE_USER_DETAILS_EDITABLE(defaultEnabled = true),
    IS_PERSONAL_SCOPE_ENABLED(defaultEnabled = true),
    IS_SUBSCRIPTION_ENABLED(defaultEnabled = true);

    val labelKey: String get() = "feature.${name.lowercase()}.label"

    val descriptionKey: String get() = "feature.${name.lowercase()}.description"

    @JsonValue
    fun wireValue(): String = name

    companion object {
        fun of(key: String): FeatureFlag? = entries.firstOrNull { it.name == key }
    }
}

data class FeatureGuard(val method: HttpMethod?, val pattern: String, val flag: FeatureFlag)

object FeatureGuards {

    val all = listOf(
        FeatureGuard(HttpMethod.PATCH, "/api/v1/auth/me", FeatureFlag.ARE_USER_DETAILS_EDITABLE),
        FeatureGuard(null, "/api/v1/boards/**", FeatureFlag.IS_PERSONAL_SCOPE_ENABLED),
        FeatureGuard(null, "/api/v1/subscriptions/**", FeatureFlag.IS_SUBSCRIPTION_ENABLED),
    )
}
