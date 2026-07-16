package app.corkboard.auth

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.stereotype.Service

@Service
class PasswordService {

    private val encoder = Argon2PasswordEncoder(16, 32, 1, 19456, 2)

    private val breached: Set<String> =
        javaClass.getResourceAsStream("/auth/breached-passwords.txt")!!
            .bufferedReader()
            .useLines { lines -> lines.map { it.trim() }.filterTo(HashSet()) { it.isNotEmpty() } }

    fun hash(raw: String): String = encoder.encode(raw)

    fun verify(raw: String, hash: String): Boolean = encoder.matches(raw, hash)

    fun isBreached(raw: String): Boolean = raw in breached || raw.lowercase() in breached
}
