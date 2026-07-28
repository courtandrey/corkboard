package app.corkboard.notifier.kafka

import app.corkboard.notifier.config.NotifierProperties
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.TopicPartition
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
    fun kafkaTemplate(properties: KafkaProperties): KafkaTemplate<String, String> {
        val configs = properties.buildProducerProperties(null).toMutableMap()
        configs[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configs[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        return KafkaTemplate(DefaultKafkaProducerFactory(configs))
    }

    @Bean
    fun errorHandler(template: KafkaTemplate<String, String>): CommonErrorHandler {
        val backOff = ExponentialBackOffWithMaxRetries(props.kafka.maxAttempts - 1).apply {
            setInitialInterval(props.kafka.backoffMillis)
            setMultiplier(props.kafka.backoffMultiplier)
            setMaxInterval(60_000)
        }
        val recoverer = DeadLetterPublishingRecoverer(template) { _, _ ->
            TopicPartition(props.kafka.deadLetterTopic, -1)
        }
        return DefaultErrorHandler(recoverer, backOff).apply {
            addNotRetryableExceptions(InvalidEmailEventException::class.java)
        }
    }
}
