package app.corkboard.events

import app.corkboard.auth.SessionAuthentication
import java.util.UUID
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/events/{id}/vote")
class VoteController(private val votes: VoteService) {

    @PreAuthorize("hasAuthority('EVENT_VOTE')")
    @PostMapping
    fun toggle(@PathVariable id: UUID, auth: SessionAuthentication): VoteResponse =
        votes.toggle(id, auth.user.userId)
}
