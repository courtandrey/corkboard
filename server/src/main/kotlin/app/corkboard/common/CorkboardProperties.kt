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
    val seedDemoPassword: String = "",
    val seedForce: Boolean = false,
) {
    data class AuthRate(val perIp: Int, val perEmail: Int)

    data class Notifications(
        val enabled: Boolean = true,
        val topic: String = "corkboard.emails.v1",
    )

    val googleAuthEnabled: Boolean
        get() = googleClientId.isNotBlank()
}
