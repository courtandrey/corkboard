package app.corkboard.notifier.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "notifier")
data class NotifierProperties(
    val apiKey: String,
    val transport: Transport = Transport.LOG,
    val from: String = "",
    val fromName: String = "",
    val replyTo: String = "",
    val maxAttempts: Int = 3,
    val retryBackoffMillis: Long = 250,
    val limits: Limits = Limits(),
) {
    enum class Transport { LOG, SMTP }

    data class Limits(
        val subjectMax: Int = 200,
        val textMax: Int = 100_000,
        val htmlMax: Int = 400_000,
    )

    val sender: String
        get() = from.ifBlank { "corkboard@localhost" }
}
