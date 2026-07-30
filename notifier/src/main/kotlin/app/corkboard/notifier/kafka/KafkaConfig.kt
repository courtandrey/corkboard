package app.corkboard.notifier.kafka

import app.corkboard.notifications.avro.NotificationRequested
import app.corkboard.notifier.config.NotifierProperties
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.CommonErrorHandler
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer

@Configuration
@ConditionalOnProperty(prefix = "notifier.kafka", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class KafkaConfig(private val props: NotifierProperties) {

    @Bean
    fun emailsTopic() = TopicBuilder.name(props.kafka.topic)
        .partitions(props.kafka.partitions)
        .replicas(1)
        .build()

    @Bean
    fun emailsDeadLetterTopic() = TopicBuilder.name(props.kafka.deadLetterTopic)
        .partitions(props.kafka.partitions)
        .replicas(1)
        .build()

    @Bean
    fun kafkaTemplate(properties: KafkaProperties): KafkaTemplate<String, Any> {
        val configs = properties.buildProducerProperties(null).toMutableMap()
        configs[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        val values = DelegatingByTypeSerializer(
            mapOf(
                ByteArray::class.java to ByteArraySerializer(),
                NotificationRequested::class.java to NotificationRequestedSerializer(),
            ),
        )
        return KafkaTemplate(DefaultKafkaProducerFactory(configs, StringSerializer(), values))
    }

    @Bean
    fun errorHandler(template: KafkaTemplate<String, Any>): CommonErrorHandler {
        val backOff = ExponentialBackOffWithMaxRetries(props.kafka.maxAttempts - 1).apply {
            setInitialInterval(props.kafka.backoffMillis)
            setMultiplier(props.kafka.backoffMultiplier)
            setMaxInterval(60_000)
        }
        val recoverer = DeadLetterPublishingRecoverer(template) { _, _ ->
            TopicPartition(props.kafka.deadLetterTopic, -1)
        }
        return DefaultErrorHandler(recoverer, backOff).apply {
            addNotRetryableExceptions(InvalidNotificationException::class.java)
        }
    }
}
