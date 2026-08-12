package app.corkboard.auth

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority

class SessionAuthentication(
    val user: SessionUser,
    val transport: SessionTransport,
) : AbstractAuthenticationToken(
    user.permissions.map { SimpleGrantedAuthority(it.name) },
) {

    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any? = null

    override fun getPrincipal(): Any = user

    override fun getName(): String = user.userId.toString()
}
