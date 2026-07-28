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
    val sendConcurrency: Int = 8,
    val limits: Limits = Limits(),
    val rate: Rate = Rate(),
    val kafka: Kafka = Kafka(),
) {
    enum class Transport { LOG, SMTP }

    data class Rate(
        val perSecond: Int = 10,
        val burst: Int = 0,
        val httpMaxWaitMillis: Long = 2_000,
    ) {
        val capacity: Int
            get() = if (burst > 0) burst else perSecond

        val limited: Boolean
            get() = perSecond > 0
    }

    data class Kafka(
        val enabled: Boolean = true,
        val topic: String = "corkboard.emails.v1",
        val group: String = "corkboard-notifier",
        val concurrency: Int = 2,
        val partitions: Int = 3,
        val maxAttempts: Int = 4,
        val backoffMillis: Long = 2_000,
        val backoffMultiplier: Double = 2.0,
    ) {
        val deadLetterTopic: String
            get() = "$topic.DLT"
    }

    data class Limits(
        val subjectMax: Int = 200,
        val textMax: Int = 100_000,
        val htmlMax: Int = 400_000,
    )

    val sender: String
        get() = from.ifBlank { "corkboard@localhost" }
}
