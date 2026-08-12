package app.corkboard.auth

import com.fasterxml.jackson.annotation.JsonValue

enum class Permission {
    EVENT_HIDE,
    EVENT_CREATE,
    EVENT_VOTE,
    EVENT_REPORT,
    EVENT_APPLY,
    MESSAGE_SEND,
    EVENT_TAKE_DOWN_ANY,
    REPORT_QUEUE_VIEW,
    ROLE_MANAGE;

    @JsonValue
    fun wireValue(): String = name

    companion object {
        fun of(name: String): Permission? = entries.firstOrNull { it.name == name }
    }
}

object Roles {
    const val RESIDENT = "resident"

    const val VERIFIED_RESIDENT = "verified_resident"

    const val MODERATOR = "moderator"
    const val ADMIN = "admin"
}
