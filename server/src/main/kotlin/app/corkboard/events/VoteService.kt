package app.corkboard.events

import app.corkboard.common.ApiException
import app.corkboard.common.ProblemCode
import app.corkboard.jooq.tables.references.EVENTS
import app.corkboard.jooq.tables.references.VOTES
import java.util.UUID
import org.jooq.DSLContext
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class VoteResponse(val score: Int, val voted: Boolean)

@Service
class VoteService(
    private val dsl: DSLContext,
    private val events: EventService,
) {

    @Transactional
    fun toggle(eventId: UUID, userId: UUID): VoteResponse {
        val authorId = events.requireSharedBoardAuthor(eventId, userId)
        if (authorId == userId) {
            throw ApiException(HttpStatus.CONFLICT, ProblemCode.OWN_EVENT)
        }
        val deleted = dsl.deleteFrom(VOTES)
            .where(VOTES.USER_ID.eq(userId), VOTES.EVENT_ID.eq(eventId))
            .execute()
        val voted = deleted == 0
        if (voted) {
            dsl.insertInto(VOTES)
                .set(VOTES.USER_ID, userId)
                .set(VOTES.EVENT_ID, eventId)
                .execute()
        }
        val score = dsl.select(EVENTS.SCORE).from(EVENTS)
            .where(EVENTS.ID.eq(eventId)).fetchOne(EVENTS.SCORE)!!
        return VoteResponse(score, voted)
    }
}
