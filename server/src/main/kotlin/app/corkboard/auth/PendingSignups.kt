package app.corkboard.auth

import app.corkboard.common.CorkboardProperties
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.springframework.stereotype.Service

private const val ALGORITHM = "HmacSHA256"
private const val SEPARATOR = '\u001F'
private val TTL: Duration = Duration.ofMinutes(20)

@Service
class PendingSignups(props: CorkboardProperties, private val clock: Clock) {

    private val key = SecretKeySpec(
        props.googleClientSecret.ifBlank { "no-google-no-signups" }.toByteArray(StandardCharsets.UTF_8),
        ALGORITHM,
    )
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun issue(profile: GoogleProfile): String {
        val body = listOf(
            profile.sub,
            profile.email,
            profile.emailVerified.toString(),
            profile.name.orEmpty(),
            (clock.millis() + TTL.toMillis()).toString(),
        ).joinToString(SEPARATOR.toString())
        val payload = encoder.encodeToString(body.toByteArray(StandardCharsets.UTF_8))
        return "$payload.${sign(payload)}"
    }

    fun open(token: String): GoogleProfile? {
        val payload = token.substringBefore('.', "")
        val signature = token.substringAfter('.', "")
        if (payload.isEmpty() || signature.isEmpty()) return null
        if (!constantTimeEquals(sign(payload), signature)) return null

        val parts = runCatching { String(decoder.decode(payload), StandardCharsets.UTF_8) }
            .getOrNull()?.split(SEPARATOR) ?: return null
        if (parts.size != 5) return null
        val expiresAt = parts[4].toLongOrNull() ?: return null
        if (expiresAt <= clock.millis()) return null

        return GoogleProfile(
            sub = parts[0],
            email = parts[1],
            emailVerified = parts[2].toBoolean(),
            name = parts[3].ifEmpty { null },
        )
    }

    private fun sign(payload: String): String {
        val mac = Mac.getInstance(ALGORITHM).apply { init(key) }
        return encoder.encodeToString(mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8)))
    }

    private fun constantTimeEquals(one: String, other: String): Boolean =
        java.security.MessageDigest.isEqual(
            one.toByteArray(StandardCharsets.UTF_8),
            other.toByteArray(StandardCharsets.UTF_8),
        )
}
