package app.corkboard.scopes

import app.corkboard.meta.EventType
import com.fasterxml.jackson.annotation.JsonValue

enum class ScopeKind(val types: List<EventType>) {
    GLOBAL(
        listOf(
            EventType.LOST_FOUND,
            EventType.ACTIVITY,
            EventType.CLUB,
            EventType.HELP,
            EventType.GIVEAWAY,
            EventType.HAPPENING,
            EventType.NOTICE,
        ),
    ),
    PERSONAL(
        listOf(
            EventType.NOTICE,
            EventType.PLAN,
            EventType.MEMORY,
        ),
    );

    @get:JsonValue
    val key: String = name.lowercase()

    val labelKey: String = "scope.$key"

    fun allows(type: EventType): Boolean = type in types

    companion object {
        fun fromKey(key: String): ScopeKind? = entries.firstOrNull { it.key == key }
    }
}

data class Subscription(
    val scopeId: java.util.UUID,
    val ownerId: java.util.UUID,
    val handle: String,
    val displayName: String,
    val avatarSeed: String,
    val memberSince: java.time.Instant,
)
