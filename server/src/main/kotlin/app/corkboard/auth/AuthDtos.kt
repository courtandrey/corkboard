package app.corkboard.auth

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

enum class SessionTransport {
    @JsonProperty("cookie")
    COOKIE,

    @JsonProperty("bearer")
    BEARER,
}

const val HANDLE_PATTERN = "^[A-Za-z0-9_]{3,30}$"

data class RegisterRequest(
    @field:NotBlank @field:Email @field:Size(max = 254)
    val email: String,
    @field:Size(min = 8, max = 128)
    val password: String,
    @field:NotBlank @field:Size(min = 1, max = 50)
    val displayName: String,
    @field:Pattern(regexp = HANDLE_PATTERN)
    val handle: String,
    val transport: SessionTransport? = null,
)

data class CompleteSignupRequest(
    @field:NotBlank
    val token: String,
    @field:NotBlank @field:Size(min = 1, max = 50)
    val displayName: String,
    @field:Pattern(regexp = HANDLE_PATTERN)
    val handle: String,
    val transport: SessionTransport? = null,
)

data class LoginRequest(
    @field:NotBlank @field:Email @field:Size(max = 254)
    val email: String,
    @field:NotBlank @field:Size(max = 128)
    val password: String,
    val transport: SessionTransport? = null,
)

data class UpdateProfileRequest(
    @field:NotBlank @field:Size(min = 1, max = 50)
    val displayName: String,
)

data class UserResponse(
    val id: UUID,
    val email: String,
    val handle: String,
    val displayName: String,
    val avatarSeed: String,
    val emailVerified: Boolean,
    val createdAt: Instant,
    val roles: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AuthResponse(
    val user: UserResponse,
    val token: String? = null,
)
