package app.corkboard.common

import com.fasterxml.jackson.annotation.JsonValue

enum class ProblemCode {
    UNAUTHENTICATED,
    FORBIDDEN,
    ORIGIN_REJECTED,
    INVALID_CREDENTIALS,
    EMAIL_TAKEN,
    BREACHED_PASSWORD,
    RATE_LIMITED,
    OWN_EVENT,
    EDIT_LOCKED,
    EXPIRY_TOO_FAR,
    VALIDATION_FAILED,
    BAD_REQUEST,
    NOT_FOUND,
    INTERNAL;

    @JsonValue
    fun wireValue(): String = name.lowercase()
}
