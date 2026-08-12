package app.corkboard.messaging

import app.corkboard.auth.EmailVerified
import app.corkboard.features.FeatureFlagsChanged
import app.corkboard.notifications.NotificationCreated
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.PingMessage
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

@Component
class WsGateway(private val objectMapper: ObjectMapper) : TextWebSocketHandler() {

    private val log = LoggerFactory.getLogger(javaClass)
    private val sessions = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val userId = session.attributes["userId"] as? UUID ?: run {
            session.close(CloseStatus.POLICY_VIOLATION)
            return
        }
        sessions.computeIfAbsent(userId) { CopyOnWriteArraySet() }.add(session)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val userId = session.attributes["userId"] as? UUID ?: return
        sessions[userId]?.remove(session)
        if (sessions[userId]?.isEmpty() == true) sessions.remove(userId, emptySet<WebSocketSession>())
    }

    @TransactionalEventListener(fallbackExecution = true)
    fun onNotification(event: NotificationCreated) {
        push(event.userId, "notification:new", mapOf("notification" to event.notification))
    }

    @TransactionalEventListener(fallbackExecution = true)
    fun onMessage(event: MessageCreated) {
        push(
            event.recipientId, "message:new",
            mapOf("conversationId" to event.conversationId, "message" to event.message),
        )
    }

    @TransactionalEventListener(fallbackExecution = true)
    fun onConversationRead(event: ConversationRead) {
        push(event.recipientId, "conversation:read", mapOf("conversationId" to event.conversationId))
    }

    @TransactionalEventListener(fallbackExecution = true)
    fun onEmailVerified(event: EmailVerified) {
        push(event.userId, "account:verified", emptyMap<String, Any>())
    }

    @EventListener
    fun onFeatureFlagsChanged(event: FeatureFlagsChanged) {
        broadcast("features:changed", mapOf("flags" to event.flags))
    }

    @Scheduled(fixedRate = 30_000)
    fun ping() {
        sessions.values.flatten().forEach { session ->
            runCatching { if (session.isOpen) session.sendMessage(PingMessage()) }
        }
    }

    fun push(userId: UUID, type: String, payload: Any) {
        send(sessions[userId] ?: return, type, payload)
    }

    fun broadcast(type: String, payload: Any) {
        send(sessions.values.flatten(), type, payload)
    }

    private fun send(targets: Collection<WebSocketSession>, type: String, payload: Any) {
        val frame = TextMessage(objectMapper.writeValueAsString(mapOf("type" to type, "payload" to payload)))
        targets.forEach { session ->
            runCatching {
                if (session.isOpen) session.sendMessage(frame)
            }.onFailure { log.debug("WS send failed for {}", session.id, it) }
        }
    }
}
