package app.corkboard.common

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID

object Cursors {

    fun encode(at: OffsetDateTime, id: UUID): String {
        val micros = at.toInstant().epochSecond * 1_000_000 + at.toInstant().nano / 1_000
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString("$micros:$id".toByteArray(Charsets.US_ASCII))
    }

    fun decode(cursor: String): Pair<OffsetDateTime, UUID>? = runCatching {
        val (micros, id) = String(Base64.getUrlDecoder().decode(cursor), Charsets.US_ASCII).split(':', limit = 2)
        val instant = Instant.ofEpochSecond(micros.toLong() / 1_000_000, micros.toLong() % 1_000_000 * 1_000)
        instant.atOffset(ZoneOffset.UTC) to UUID.fromString(id)
    }.getOrNull()
}
