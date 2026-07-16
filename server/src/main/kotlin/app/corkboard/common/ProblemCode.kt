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
    VALIDATION_FAILED,
    BAD_REQUEST,
    NOT_FOUND,
    INTERNAL;

    @JsonValue
    fun wireValue(): String = name.lowercase()
}
