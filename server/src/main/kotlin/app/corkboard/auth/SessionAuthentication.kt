package app.corkboard.auth

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority

class SessionAuthentication(
    val user: SessionUser,
    val transport: SessionTransport,
) : AbstractAuthenticationToken(listOf(SimpleGrantedAuthority("ROLE_USER"))) {

    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any? = null

    override fun getPrincipal(): Any = user

    override fun getName(): String = user.userId.toString()
}
