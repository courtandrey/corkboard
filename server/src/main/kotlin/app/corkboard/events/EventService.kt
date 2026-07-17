package app.corkboard.events

import app.corkboard.common.ApiException
import app.corkboard.common.ProblemCode
import app.corkboard.jooq.enums.EventStatus as DbEventStatus
import app.corkboard.jooq.enums.EventType as DbEventType
import app.corkboard.jooq.tables.references.APPLICATIONS
import app.corkboard.jooq.tables.references.EVENTS
import app.corkboard.jooq.tables.references.EVENT_HIDES
import app.corkboard.jooq.tables.references.USERS
import app.corkboard.jooq.tables.references.VOTES
import app.corkboard.meta.EventType
import app.corkboard.tags.TagService
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Geometry
import org.jooq.impl.DSL
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EventService(
    private val dsl: DSLContext,
    private val tags: TagService,
    private val clock: Clock,
) {

    companion object {
        const val MAX_EXPIRY_DAYS = 90L
        private val HIDDEN_STATUSES = setOf(DbEventStatus.removed, DbEventStatus.under_review)
        private val LNG = DSL.field("ST_X({0})", Double::class.java, EVENTS.LOCATION)
        private val LAT = DSL.field("ST_Y({0})", Double::class.java, EVENTS.LOCATION)
    }

    private data class EventRow(
        val id: UUID,
        val authorId: UUID,
        val type: DbEventType,
        val status: DbEventStatus,
        val title: String,
        val body: String,
        val lng: Double,
        val lat: Double,
        val applyable: Boolean,
        val score: Int,
        val applicationCount: Int,
        val expiresAt: OffsetDateTime,
        val resolvedAt: OffsetDateTime?,
        val createdAt: OffsetDateTime,
        val updatedAt: OffsetDateTime,
    )

    @Transactional
    fun create(authorId: UUID, req: CreateEventRequest): EventDetail {
        val title = req.title.trim()
        val body = req.body.trim()
        if (title.length < 3 || body.isEmpty()) {
            throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.VALIDATION_FAILED)
        }
        checkExpiry(req.expiresAt)

        val id = dsl.insertInto(EVENTS)
            .set(EVENTS.AUTHOR_ID, authorId)
            .set(EVENTS.TYPE, DbEventType.valueOf(req.type.key))
            .set(EVENTS.TITLE, title)
            .set(EVENTS.BODY, body)
            .set(EVENTS.LOCATION, point(req.location))
            .set(EVENTS.APPLYABLE, req.applyable)
            .set(EVENTS.EXPIRES_AT, req.expiresAt.atOffset(ZoneOffset.UTC))
            .returning(EVENTS.ID)
            .fetchOne(EVENTS.ID)!!

        if (req.tags.isNotEmpty()) {
            tags.replaceEventTags(id, req.tags)
        }
        return detail(id, authorId)
    }

    fun detail(id: UUID, viewerId: UUID?): EventDetail {
        val event = fetchEvent(id)
        if (event.status in HIDDEN_STATUSES && event.authorId != viewerId) {
            throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)
        }
        return toDetail(event, viewerId)
    }

    @Transactional
    fun update(id: UUID, viewerId: UUID, req: UpdateEventRequest): EventDetail {
        val event = fetchEvent(id)
        if (event.status in HIDDEN_STATUSES && event.authorId != viewerId) {
            throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)
        }
        if (event.authorId != viewerId) {
            throw ApiException(HttpStatus.FORBIDDEN, ProblemCode.FORBIDDEN)
        }
        if ((req.type != null || req.location != null) && event.applicationCount > 0) {
            throw ApiException(HttpStatus.CONFLICT, ProblemCode.EDIT_LOCKED)
        }
        req.expiresAt?.let(::checkExpiry)

        val title = req.title?.trim()
        val body = req.body?.trim()
        if (title != null && title.length < 3 || body != null && body.isEmpty()) {
            throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.VALIDATION_FAILED)
        }

        val update = dsl.update(EVENTS).set(EVENTS.UPDATED_AT, OffsetDateTime.now(clock))
        req.type?.let { update.set(EVENTS.TYPE, DbEventType.valueOf(it.key)) }
        title?.let { update.set(EVENTS.TITLE, it) }
        body?.let { update.set(EVENTS.BODY, it) }
        req.location?.let { update.set(EVENTS.LOCATION, point(it)) }
        req.applyable?.let { update.set(EVENTS.APPLYABLE, it) }
        req.expiresAt?.let { update.set(EVENTS.EXPIRES_AT, it.atOffset(ZoneOffset.UTC)) }
        update.where(EVENTS.ID.eq(id)).execute()

        req.tags?.let { tags.replaceEventTags(id, it) }
        return detail(id, viewerId)
    }

    private fun fetchEvent(id: UUID): EventRow =
        dsl.select(
            EVENTS.ID, EVENTS.AUTHOR_ID, EVENTS.TYPE, EVENTS.STATUS, EVENTS.TITLE, EVENTS.BODY,
            LNG, LAT, EVENTS.APPLYABLE, EVENTS.SCORE, EVENTS.APPLICATION_COUNT,
            EVENTS.EXPIRES_AT, EVENTS.RESOLVED_AT, EVENTS.CREATED_AT, EVENTS.UPDATED_AT,
        )
            .from(EVENTS)
            .where(EVENTS.ID.eq(id))
            .fetchOne { r ->
                EventRow(
                    id = r[EVENTS.ID]!!,
                    authorId = r[EVENTS.AUTHOR_ID]!!,
                    type = r[EVENTS.TYPE]!!,
                    status = r[EVENTS.STATUS]!!,
                    title = r[EVENTS.TITLE]!!,
                    body = r[EVENTS.BODY]!!,
                    lng = r[LNG]!!,
                    lat = r[LAT]!!,
                    applyable = r[EVENTS.APPLYABLE]!!,
                    score = r[EVENTS.SCORE]!!,
                    applicationCount = r[EVENTS.APPLICATION_COUNT]!!,
                    expiresAt = r[EVENTS.EXPIRES_AT]!!,
                    resolvedAt = r[EVENTS.RESOLVED_AT],
                    createdAt = r[EVENTS.CREATED_AT]!!,
                    updatedAt = r[EVENTS.UPDATED_AT]!!,
                )
            } ?: throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)

    private fun toDetail(event: EventRow, viewerId: UUID?): EventDetail {
        val author = dsl.select(USERS.DISPLAY_NAME, USERS.AVATAR_SEED, USERS.CREATED_AT)
            .from(USERS).where(USERS.ID.eq(event.authorId)).fetchOne()!!

        return EventDetail(
            id = event.id,
            type = EventType.fromKey(event.type.literal)!!,
            status = EventStatus.fromDb(event.status.literal),
            title = event.title,
            body = event.body,
            location = LatLng(event.lng, event.lat),
            applyable = event.applyable,
            score = event.score,
            applicationCount = event.applicationCount,
            tags = tags.eventTags(event.id).map { (name, slug) -> TagRef(name, slug) },
            author = AuthorCard(
                displayName = author[USERS.DISPLAY_NAME]!!,
                avatarSeed = author[USERS.AVATAR_SEED]!!,
                memberSince = author[USERS.CREATED_AT]!!.toInstant(),
            ),
            viewerState = viewerState(event.id, event.authorId, viewerId),
            expiresAt = event.expiresAt.toInstant(),
            resolvedAt = event.resolvedAt?.toInstant(),
            createdAt = event.createdAt.toInstant(),
            updatedAt = event.updatedAt.toInstant(),
        )
    }

    private fun viewerState(eventId: UUID, authorId: UUID, viewerId: UUID?): ViewerState {
        if (viewerId == null) return ViewerState(voted = false, hidden = false, applied = false, isAuthor = false)
        return ViewerState(
            voted = dsl.fetchExists(VOTES, VOTES.USER_ID.eq(viewerId), VOTES.EVENT_ID.eq(eventId)),
            hidden = dsl.fetchExists(EVENT_HIDES, EVENT_HIDES.USER_ID.eq(viewerId), EVENT_HIDES.EVENT_ID.eq(eventId)),
            applied = dsl.fetchExists(APPLICATIONS, APPLICATIONS.APPLICANT_ID.eq(viewerId), APPLICATIONS.EVENT_ID.eq(eventId)),
            isAuthor = viewerId == authorId,
        )
    }

    private fun checkExpiry(expiresAt: Instant) {
        if (expiresAt.isAfter(Instant.now(clock).plus(Duration.ofDays(MAX_EXPIRY_DAYS)))) {
            throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.EXPIRY_TOO_FAR)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun point(location: LatLng): Field<Geometry?> =
        DSL.field(
            "ST_SetSRID(ST_MakePoint({0}, {1}), 4326)",
            Geometry::class.java,
            DSL.`val`(location.lng), DSL.`val`(location.lat),
        ) as Field<Geometry?>
}
