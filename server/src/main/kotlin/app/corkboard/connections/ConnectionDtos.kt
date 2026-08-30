package app.corkboard.connections

import com.fasterxml.jackson.annotation.JsonValue
import java.time.Instant
import java.util.UUID

enum class ConnectionState(@get:JsonValue val key: String) {
    NONE("none"),
    INCOMING("incoming"),
    OUTGOING("outgoing"),
    CONNECTED("connected");
}

data class ConnectionItem(
    val id: UUID,
    val person: PersonCard,
    val since: Instant,
)

data class PersonCard(
    val id: UUID,
    val handle: String,
    val displayName: String,
    val avatarSeed: String,
    val memberSince: Instant,
    val state: ConnectionState,
    val connectionId: UUID? = null,
)

data class ConnectionsResponse(
    val connected: List<ConnectionItem>,
    val incoming: List<ConnectionItem>,
    val outgoing: List<ConnectionItem>,
)

data class PeopleResponse(val items: List<PersonCard>)

data class ConnectionRequest(val userId: UUID)

data class ConnectionResponse(val id: UUID, val state: ConnectionState)
