package app.corkboard.auth

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

enum class SessionTransport {
    @JsonProperty("cookie")
    COOKIE,

    @JsonProperty("bearer")
    BEARER,
}

data class RegisterRequest(
    @field:NotBlank @field:Email @field:Size(max = 254)
    val email: String,
    @field:Size(min = 8, max = 128)
    val password: String,
    @field:NotBlank @field:Size(min = 1, max = 50)
    val displayName: String,
    val transport: SessionTransport? = null,
)

data class LoginRequest(
    @field:NotBlank @field:Email @field:Size(max = 254)
    val email: String,
    @field:NotBlank @field:Size(max = 128)
    val password: String,
    val transport: SessionTransport? = null,
)

data class UserResponse(
    val id: UUID,
    val email: String,
    val displayName: String,
    val avatarSeed: String,
    val createdAt: Instant,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AuthResponse(
    val user: UserResponse,
    val token: String? = null,
)
