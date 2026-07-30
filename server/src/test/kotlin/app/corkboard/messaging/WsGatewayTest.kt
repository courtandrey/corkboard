package app.corkboard.messaging

import app.corkboard.ApiTestBase
import java.net.URI
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpMethod
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler

class WsGatewayTest : ApiTestBase() {

    @LocalServerPort
    var port: Int = 0

    private class CollectingHandler : TextWebSocketHandler() {
        val frames = LinkedBlockingQueue<String>()
        override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
            frames.add(message.payload)
        }
    }

    private fun bearerToken(): Pair<String, org.springframework.http.HttpHeaders> {
        val res = sendJson(
            HttpMethod.POST, "/api/v1/auth/register",
            mapOf(
                "email" to "ws-${UUID.randomUUID()}@example.com",
                "password" to "Pw-${UUID.randomUUID()}",
                "displayName" to "WS User",
                "transport" to "bearer",
            ),
        )
        check(res.statusCode.value() == 201) { "register failed: ${res.body}" }
        val body = json(res)
        markEmailVerified(UUID.fromString(body["user"]["id"].asText()))
        val token = body["token"].asText()
        val headers = org.springframework.http.HttpHeaders().apply { setBearerAuth(token) }
        return token to headers
    }

    @Test
    fun `authenticated socket receives notification and message frames after an apply`() {
        val (authorToken, authorHeaders) = bearerToken()
        val applicant = registerUser("WS Applicant")

        val eventRes = sendJson(
            HttpMethod.POST, "/api/v1/events",
            mapOf(
                "type" to "help",
                "title" to "WS frame test note",
                "body" to "Apply so my socket beeps.",
                "location" to mapOf("lng" to 36.3, "lat" to 66.1),
                "applyable" to true,
                "expiresAt" to java.time.Instant.now().plus(10, java.time.temporal.ChronoUnit.DAYS).toString(),
            ),
            authorHeaders,
        )
        val eventId = json(eventRes)["id"].asText()

        val handler = CollectingHandler()
        val session = StandardWebSocketClient()
            .execute(handler, "ws://localhost:$port/ws?token=$authorToken")
            .get(5, TimeUnit.SECONDS)
        try {
            val applied = sendJson(
                HttpMethod.POST, "/api/v1/events/$eventId/apply",
                mapOf("message" to "Beep beep."), applicant.headers,
            )
            assertThat(applied.statusCode.value()).isEqualTo(201)

            val frames = mutableListOf<String>()
            repeat(2) {
                val frame = handler.frames.poll(5, TimeUnit.SECONDS)
                assertThat(frame).isNotNull
                frames.add(frame!!)
            }
            val types = frames.map { mapper.readTree(it)["type"].asText() }
            assertThat(types).containsExactlyInAnyOrder("notification:new", "message:new")
            val notification = frames.first { mapper.readTree(it)["type"].asText() == "notification:new" }
            assertThat(mapper.readTree(notification)["payload"]["notification"]["kind"].asText())
                .isEqualTo("application_received")
        } finally {
            session.close(CloseStatus.NORMAL)
        }
    }

    @Test
    fun `unauthenticated handshake is refused`() {
        val handler = CollectingHandler()
        val result = runCatching {
            StandardWebSocketClient()
                .execute(handler, URI.create("ws://localhost:$port/ws").toString())
                .get(5, TimeUnit.SECONDS)
        }
        assertThat(result.isFailure).isTrue
    }
}
