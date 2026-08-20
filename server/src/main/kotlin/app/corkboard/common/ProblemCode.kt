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
    EMAIL_UNVERIFIED,
    FEATURE_DISABLED,
    SCOPE_FORBIDDEN,
    TYPE_NOT_IN_SCOPE,
    PLACE_SEARCH_OFF,
    ROLE_NOT_GRANTABLE,
    OWN_EVENT,
    NOT_APPLYABLE,
    ALREADY_APPLIED,
    INVALID_STATUS,
    EDIT_LOCKED,
    EXPIRY_TOO_FAR,
    VALIDATION_FAILED,
    BAD_REQUEST,
    NOT_FOUND,
    INTERNAL;

    @JsonValue
    fun wireValue(): String = name.lowercase()
}
