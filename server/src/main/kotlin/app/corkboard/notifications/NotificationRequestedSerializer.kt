package app.corkboard.notifications

import app.corkboard.notifications.avro.NotificationRequested
import org.apache.kafka.common.serialization.Serializer

class NotificationRequestedSerializer : Serializer<NotificationRequested> {

    override fun serialize(topic: String, data: NotificationRequested?): ByteArray? =
        data?.toByteBuffer()?.let { buffer -> ByteArray(buffer.remaining()).also { buffer.get(it) } }
}
