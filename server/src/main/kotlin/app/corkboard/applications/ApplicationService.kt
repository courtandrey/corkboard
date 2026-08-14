package app.corkboard.applications

import app.corkboard.common.ApiException
import app.corkboard.common.ProblemCode
import app.corkboard.events.AuthorCard
import app.corkboard.events.EventSnippet
import app.corkboard.events.EventStatus
import app.corkboard.jooq.enums.ApplicationStatus as DbApplicationStatus
import app.corkboard.jooq.enums.EventStatus as DbEventStatus
import app.corkboard.jooq.tables.references.APPLICATIONS
import app.corkboard.jooq.tables.references.CONVERSATIONS
import app.corkboard.jooq.tables.references.EVENTS
import app.corkboard.jooq.tables.references.MESSAGES
import app.corkboard.jooq.tables.references.USERS
import app.corkboard.messaging.ApplicationStatus
import app.corkboard.messaging.ConversationService
import app.corkboard.messaging.MessageCreated
import app.corkboard.notifications.NotificationKind
import app.corkboard.notifications.NotificationService
import app.corkboard.scopes.ScopeService
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ApplicationService(
    private val dsl: DSLContext,
    private val scopes: ScopeService,
    private val conversations: ConversationService,
    private val notifications: NotificationService,
    private val publisher: ApplicationEventPublisher,
) {

    companion object {
        private val HIDDEN_STATUSES = setOf(DbEventStatus.removed, DbEventStatus.under_review)
    }

    @Transactional
    fun apply(eventId: UUID, applicantId: UUID, message: String): ApplyResponse {
        val event = dsl.select(EVENTS.AUTHOR_ID, EVENTS.TITLE, EVENTS.STATUS, EVENTS.APPLYABLE, EVENTS.SCOPE_ID)
            .from(EVENTS).where(EVENTS.ID.eq(eventId)).fetchOne()
            ?: throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)
        scopes.requireSharedBoard(event[EVENTS.SCOPE_ID]!!)
        val authorId = event[EVENTS.AUTHOR_ID]!!
        if (authorId == applicantId) {
            throw ApiException(HttpStatus.CONFLICT, ProblemCode.OWN_EVENT)
        }
        if (event[EVENTS.STATUS] in HIDDEN_STATUSES) {
            throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)
        }
        if (event[EVENTS.STATUS] != DbEventStatus.active || event[EVENTS.APPLYABLE] != true) {
            throw ApiException(HttpStatus.CONFLICT, ProblemCode.NOT_APPLYABLE)
        }
        val body = message.trim()
        if (body.isEmpty()) {
            throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.VALIDATION_FAILED)
        }

        val application = try {
            dsl.insertInto(APPLICATIONS)
                .set(APPLICATIONS.EVENT_ID, eventId)
                .set(APPLICATIONS.APPLICANT_ID, applicantId)
                .returning()
                .fetchOne()!!
        } catch (e: DuplicateKeyException) {
            throw ApiException(HttpStatus.CONFLICT, ProblemCode.ALREADY_APPLIED)
        }

        val conversationId = dsl.insertInto(CONVERSATIONS)
            .set(CONVERSATIONS.APPLICATION_ID, application.id)
            .set(CONVERSATIONS.EVENT_ID, eventId)
            .set(CONVERSATIONS.OWNER_ID, authorId)
            .set(CONVERSATIONS.APPLICANT_ID, applicantId)
            .returning(CONVERSATIONS.ID)
            .fetchOne(CONVERSATIONS.ID)!!

        val firstMessage = conversations.insertMessage(conversationId, applicantId, body)

        notifications.create(
            authorId,
            NotificationKind.APPLICATION_RECEIVED,
            mapOf(
                "eventId" to eventId.toString(),
                "eventTitle" to event[EVENTS.TITLE],
                "applicationId" to application.id.toString(),
                "conversationId" to conversationId.toString(),
            ),
        )
        publisher.publishEvent(MessageCreated(authorId, conversationId, firstMessage))

        return ApplyResponse(
            application = ApplicationResponse(
                id = application.id!!,
                eventId = eventId,
                status = ApplicationStatus.fromDb(application.status!!.literal),
                createdAt = application.createdAt!!.toInstant(),
            ),
            conversationId = conversationId,
        )
    }

    @Transactional
    fun updateStatus(applicationId: UUID, actorId: UUID, newStatus: ApplicationStatus): ApplicationResponse {
        if (newStatus != ApplicationStatus.ACCEPTED && newStatus != ApplicationStatus.DECLINED) {
            throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.VALIDATION_FAILED)
        }
        val row = fetchWithEvent(applicationId)
        if (actorId == row.applicantId) {
            throw ApiException(HttpStatus.FORBIDDEN, ProblemCode.FORBIDDEN)
        }
        if (actorId != row.authorId) {
            throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)
        }
        if (row.status != DbApplicationStatus.pending) {
            throw ApiException(HttpStatus.CONFLICT, ProblemCode.INVALID_STATUS)
        }
        dsl.update(APPLICATIONS)
            .set(APPLICATIONS.STATUS, DbApplicationStatus.valueOf(newStatus.key))
            .where(APPLICATIONS.ID.eq(applicationId))
            .execute()

        notifications.create(
            row.applicantId,
            NotificationKind.APPLICATION_STATUS,
            mapOf(
                "eventId" to row.eventId.toString(),
                "eventTitle" to row.eventTitle,
                "applicationId" to applicationId.toString(),
                "conversationId" to row.conversationId?.toString(),
                "status" to newStatus.key,
            ),
        )
        return ApplicationResponse(applicationId, row.eventId, newStatus, row.createdAt)
    }

    @Transactional
    fun withdraw(applicationId: UUID, actorId: UUID): ApplicationResponse {
        val row = fetchWithEvent(applicationId)
        if (actorId != row.applicantId) {
            throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)
        }
        if (row.status != DbApplicationStatus.pending && row.status != DbApplicationStatus.accepted) {
            throw ApiException(HttpStatus.CONFLICT, ProblemCode.INVALID_STATUS)
        }
        dsl.update(APPLICATIONS)
            .set(APPLICATIONS.STATUS, DbApplicationStatus.withdrawn)
            .where(APPLICATIONS.ID.eq(applicationId))
            .execute()
        return ApplicationResponse(applicationId, row.eventId, ApplicationStatus.WITHDRAWN, row.createdAt)
    }

    fun myApplications(userId: UUID, role: ApplicationRole): MyApplicationsResponse {
        val applicantUser = USERS.`as`("applicant_user")
        val firstBody = DSL.field(
            DSL.select(MESSAGES.BODY).from(MESSAGES)
                .where(MESSAGES.CONVERSATION_ID.eq(CONVERSATIONS.ID))
                .orderBy(MESSAGES.CREATED_AT.asc(), MESSAGES.ID.asc())
                .limit(1)
        )
        val roleCond = when (role) {
            ApplicationRole.SENT -> APPLICATIONS.APPLICANT_ID.eq(userId)
            ApplicationRole.RECEIVED -> EVENTS.AUTHOR_ID.eq(userId)
        }

        val rows = dsl.select(
            APPLICATIONS.ID, APPLICATIONS.STATUS, APPLICATIONS.CREATED_AT,
            EVENTS.ID, EVENTS.TITLE, EVENTS.STATUS,
            CONVERSATIONS.ID,
            applicantUser.DISPLAY_NAME, applicantUser.AVATAR_SEED, applicantUser.CREATED_AT,
            firstBody,
        )
            .from(APPLICATIONS)
            .join(EVENTS).on(EVENTS.ID.eq(APPLICATIONS.EVENT_ID))
            .join(CONVERSATIONS).on(CONVERSATIONS.APPLICATION_ID.eq(APPLICATIONS.ID))
            .join(applicantUser).on(applicantUser.ID.eq(APPLICATIONS.APPLICANT_ID))
            .where(roleCond)
            .orderBy(EVENTS.CREATED_AT.desc(), APPLICATIONS.CREATED_AT.desc())
            .limit(200)
            .fetch()

        val groups = rows.groupBy({ r -> r[EVENTS.ID]!! }) { r -> r }.map { (_, groupRows) ->
            val first = groupRows.first()
            ApplicationGroup(
                event = EventSnippet(
                    id = first[EVENTS.ID]!!,
                    title = first[EVENTS.TITLE]!!,
                    status = EventStatus.fromDb(first[EVENTS.STATUS]!!.literal),
                ),
                applications = groupRows.map { r ->
                    ApplicationItem(
                        id = r[APPLICATIONS.ID]!!,
                        status = ApplicationStatus.fromDb(r[APPLICATIONS.STATUS]!!.literal),
                        message = r[firstBody],
                        createdAt = r[APPLICATIONS.CREATED_AT]!!.toInstant(),
                        conversationId = r[CONVERSATIONS.ID]!!,
                        applicant = if (role == ApplicationRole.RECEIVED) {
                            AuthorCard(
                                displayName = r[applicantUser.DISPLAY_NAME]!!,
                                avatarSeed = r[applicantUser.AVATAR_SEED]!!,
                                memberSince = r[applicantUser.CREATED_AT]!!.toInstant(),
                            )
                        } else null,
                    )
                },
            )
        }
        return MyApplicationsResponse(groups)
    }

    private data class ApplicationRow(
        val applicantId: UUID,
        val authorId: UUID,
        val eventId: UUID,
        val eventTitle: String,
        val status: DbApplicationStatus,
        val createdAt: java.time.Instant,
        val conversationId: UUID?,
    )

    private fun fetchWithEvent(applicationId: UUID): ApplicationRow =
        dsl.select(
            APPLICATIONS.APPLICANT_ID, APPLICATIONS.STATUS, APPLICATIONS.CREATED_AT,
            EVENTS.AUTHOR_ID, EVENTS.ID, EVENTS.TITLE,
            CONVERSATIONS.ID,
        )
            .from(APPLICATIONS)
            .join(EVENTS).on(EVENTS.ID.eq(APPLICATIONS.EVENT_ID))
            .leftJoin(CONVERSATIONS).on(CONVERSATIONS.APPLICATION_ID.eq(APPLICATIONS.ID))
            .where(APPLICATIONS.ID.eq(applicationId))
            .fetchOne { r ->
                ApplicationRow(
                    applicantId = r[APPLICATIONS.APPLICANT_ID]!!,
                    authorId = r[EVENTS.AUTHOR_ID]!!,
                    eventId = r[EVENTS.ID]!!,
                    eventTitle = r[EVENTS.TITLE]!!,
                    status = r[APPLICATIONS.STATUS]!!,
                    createdAt = r[APPLICATIONS.CREATED_AT]!!.toInstant(),
                    conversationId = r[CONVERSATIONS.ID],
                )
            } ?: throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)
}
