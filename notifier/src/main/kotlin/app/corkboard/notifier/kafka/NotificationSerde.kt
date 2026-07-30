package app.corkboard.notifier.kafka

import app.corkboard.notifications.avro.NotificationRequested
import java.nio.ByteBuffer
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serializer

class InvalidNotificationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class NotificationRequestedDeserializer : Deserializer<NotificationRequested> {

    override fun deserialize(topic: String, data: ByteArray?): NotificationRequested {
        if (data == null || data.isEmpty()) throw InvalidNotificationException("empty payload")
        return try {
            NotificationRequested.fromByteBuffer(ByteBuffer.wrap(data))
        } catch (failure: Exception) {
            throw InvalidNotificationException(
                "payload is not a NotificationRequested record: ${failure.message}",
                failure,
            )
        }
    }
}

class NotificationRequestedSerializer : Serializer<NotificationRequested> {

    override fun serialize(topic: String, data: NotificationRequested?): ByteArray? =
        data?.toByteBuffer()?.let { buffer -> ByteArray(buffer.remaining()).also { buffer.get(it) } }
}
