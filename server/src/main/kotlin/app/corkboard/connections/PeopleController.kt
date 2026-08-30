package app.corkboard.connections

import app.corkboard.auth.SessionAuthentication
import java.security.Principal
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class PeopleController(private val connections: ConnectionService) {

    @GetMapping("/{id}")
    fun person(@PathVariable id: UUID, principal: Principal?): PersonCard =
        connections.profile((principal as? SessionAuthentication)?.user?.userId, id)
}
