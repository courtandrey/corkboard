package app.corkboard.moderation

import app.corkboard.events.EventService
import app.corkboard.jooq.tables.references.EVENT_HIDES
import java.util.UUID
import org.jooq.DSLContext
import org.springframework.stereotype.Service

@Service
class HideService(
    private val dsl: DSLContext,
    private val events: EventService,
) {

    fun hide(eventId: UUID, userId: UUID) {
        events.requireSharedBoardAuthor(eventId, userId)
        dsl.insertInto(EVENT_HIDES)
            .set(EVENT_HIDES.USER_ID, userId)
            .set(EVENT_HIDES.EVENT_ID, eventId)
            .onConflictDoNothing()
            .execute()
    }

    fun unhide(eventId: UUID, userId: UUID) {
        dsl.deleteFrom(EVENT_HIDES)
            .where(EVENT_HIDES.USER_ID.eq(userId), EVENT_HIDES.EVENT_ID.eq(eventId))
            .execute()
    }
}
