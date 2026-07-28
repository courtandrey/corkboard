package app.corkboard.notifications

import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate

@Configuration
class NotificationsKafkaConfig {

    @Bean
    fun kafkaTemplate(properties: KafkaProperties): KafkaTemplate<String, String> {
        val configs = properties.buildProducerProperties(null).toMutableMap()
        configs[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configs[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        return KafkaTemplate(DefaultKafkaProducerFactory(configs))
    }
}
