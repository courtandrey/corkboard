package app.corkboard.messaging

import app.corkboard.auth.SessionCookies
import app.corkboard.auth.SessionService
import app.corkboard.common.CorkboardProperties
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.server.HandshakeInterceptor

@Configuration
@EnableWebSocket
class WsConfig(
    private val gateway: WsGateway,
    private val sessionService: SessionService,
    private val props: CorkboardProperties,
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(gateway, "/ws")
            .setAllowedOrigins(props.webOrigin)
            .addInterceptors(AuthHandshakeInterceptor(sessionService))
    }
}

class AuthHandshakeInterceptor(private val sessions: SessionService) : HandshakeInterceptor {

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        val servletRequest = (request as? ServletServerHttpRequest)?.servletRequest ?: return false
        val token = extractToken(servletRequest)
        val user = token?.let(sessions::resolve)
        if (user == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED)
            return false
        }
        attributes["userId"] = user.userId
        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?,
    ) = Unit

    private fun extractToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ", ignoreCase = true)) {
            return header.substring(7).trim().takeIf { it.isNotEmpty() }
        }
        request.getParameter("token")?.takeIf { it.isNotEmpty() }?.let { return it }
        return request.cookies
            ?.firstOrNull { it.name == SessionCookies.SESSION_COOKIE }
            ?.value?.takeIf { it.isNotEmpty() }
    }
}
