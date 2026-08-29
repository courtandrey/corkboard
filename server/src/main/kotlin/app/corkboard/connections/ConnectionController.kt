package app.corkboard.connections

import app.corkboard.auth.SessionAuthentication
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/connections")
class ConnectionController(private val connections: ConnectionService) {

    @GetMapping
    fun list(auth: SessionAuthentication): ConnectionsResponse = connections.list(auth.user.userId)

    @GetMapping("/people")
    fun search(auth: SessionAuthentication, @RequestParam q: String): PeopleResponse =
        connections.search(auth.user.userId, q)

    @PreAuthorize("hasAuthority('CONNECTION_MANAGE')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun request(auth: SessionAuthentication, @Valid @RequestBody req: ConnectionRequest): ConnectionResponse =
        connections.request(auth.user.userId, req.userId)

    @PreAuthorize("hasAuthority('CONNECTION_MANAGE')")
    @PostMapping("/{id}/accept")
    fun accept(auth: SessionAuthentication, @PathVariable id: UUID): ConnectionResponse =
        connections.accept(id, auth.user.userId)

    @PreAuthorize("hasAuthority('CONNECTION_MANAGE')")
    @PostMapping("/{id}/decline")
    fun decline(auth: SessionAuthentication, @PathVariable id: UUID): ConnectionResponse =
        connections.decline(id, auth.user.userId)
}
