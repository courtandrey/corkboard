package app.corkboard.common

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "corkboard")
data class CorkboardProperties(
    val webOrigin: String,
    val reportAutoHideThreshold: Int,
    val cookieSecure: Boolean,
    val sessionTtlDays: Long,
    val googleClientId: String,
    val googleClientSecret: String,
    val googleCallbackUrl: String,
    val authRate: AuthRate,
    val notifications: Notifications = Notifications(),
    val featureFlags: FeatureFlags = FeatureFlags(),
    val geocoder: Geocoder = Geocoder(),
    val seedDemoPassword: String = "",
    val seedForce: Boolean = false,
) {
    data class AuthRate(val perIp: Int, val perEmail: Int)

    data class FeatureFlags(val listen: Boolean = true)

    data class Geocoder(
        val url: String = "",
        val language: String = "en",
        val perIpPerMinute: Int = 60,
        val connectTimeoutMillis: Long = 2000,
        val readTimeoutMillis: Long = 4000,
    ) {
        val enabled: Boolean get() = url.isNotBlank()
    }

    data class Notifications(
        val enabled: Boolean = true,
        val topic: String = "corkboard.emails.v1",
    )

    val googleAuthEnabled: Boolean
        get() = googleClientId.isNotBlank()
}
