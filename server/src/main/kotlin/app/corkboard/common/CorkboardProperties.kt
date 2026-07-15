package app.corkboard.common

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "corkboard")
data class CorkboardProperties(
    val webOrigin: String,
    val reportAutoHideThreshold: Int,
    val googleClientId: String,
) {
    val googleAuthEnabled: Boolean
        get() = googleClientId.isNotBlank()
}
