package app.corkboard.scopes

import app.corkboard.common.ApiException
import app.corkboard.common.ProblemCode
import app.corkboard.features.FeatureFlag
import app.corkboard.features.FeatureFlagService
import app.corkboard.jooq.enums.ScopeKind as DbScopeKind
import app.corkboard.jooq.tables.references.SCOPES
import app.corkboard.jooq.tables.references.SCOPE_MEMBERS
import app.corkboard.jooq.tables.references.USERS
import jakarta.annotation.PostConstruct
import java.util.UUID
import org.jooq.DSLContext
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

@Service
class ScopeService(
    private val dsl: DSLContext,
    private val flags: FeatureFlagService,
) {

    private lateinit var global: UUID

    val globalId: UUID get() = global

    @PostConstruct
    fun load() {
        global = dsl.select(SCOPES.ID).from(SCOPES)
            .where(SCOPES.KIND.eq(DbScopeKind.global))
            .fetchOne(SCOPES.ID)
            ?: error("the global scope is missing — migration V11 creates it")
    }

    fun isGlobal(scopeId: UUID): Boolean = scopeId == global

    fun enabled(): Boolean = flags.isEnabled(FeatureFlag.IS_PERSONAL_SCOPE_ENABLED)

    fun subscriptionsEnabled(): Boolean = flags.isEnabled(FeatureFlag.IS_SUBSCRIPTION_ENABLED)

    fun kindOf(scopeId: UUID): ScopeKind =
        if (isGlobal(scopeId)) ScopeKind.GLOBAL else ScopeKind.PERSONAL

    fun boardOf(ownerId: UUID): UUID? =
        dsl.select(SCOPES.ID).from(SCOPES)
            .where(SCOPES.OWNER_ID.eq(ownerId), SCOPES.KIND.eq(DbScopeKind.personal))
            .fetchOne(SCOPES.ID)

    fun ensureBoardOf(ownerId: UUID): UUID =
        boardOf(ownerId) ?: dsl.insertInto(SCOPES)
            .set(SCOPES.KIND, DbScopeKind.personal)
            .set(SCOPES.OWNER_ID, ownerId)
            .onConflictDoNothing()
            .returning(SCOPES.ID)
            .fetchOne(SCOPES.ID)
            ?: boardOf(ownerId)!!

    fun requireBoardReadable(ownerId: UUID, viewerId: UUID?) {
        if (ownerId == viewerId) return
        requireResident(ownerId)
        if (viewerId != null && subscriptionsEnabled() && isMember(ownerId, viewerId)) return
        throw ApiException(HttpStatus.FORBIDDEN, ProblemCode.SCOPE_FORBIDDEN)
    }

    fun isMember(ownerId: UUID, viewerId: UUID): Boolean =
        dsl.fetchExists(
            dsl.selectOne().from(SCOPE_MEMBERS)
                .join(SCOPES).on(SCOPES.ID.eq(SCOPE_MEMBERS.SCOPE_ID))
                .where(SCOPES.OWNER_ID.eq(ownerId), SCOPE_MEMBERS.USER_ID.eq(viewerId)),
        )

    fun boardsReadableBy(viewerId: UUID): List<Subscription> =
        dsl.select(SCOPES.ID, SCOPES.OWNER_ID, USERS.HANDLE, USERS.DISPLAY_NAME, USERS.AVATAR_SEED, USERS.CREATED_AT)
            .from(SCOPE_MEMBERS)
            .join(SCOPES).on(SCOPES.ID.eq(SCOPE_MEMBERS.SCOPE_ID))
            .join(USERS).on(USERS.ID.eq(SCOPES.OWNER_ID))
            .where(SCOPE_MEMBERS.USER_ID.eq(viewerId))
            .orderBy(USERS.DISPLAY_NAME.asc())
            .fetch { r ->
                Subscription(
                    scopeId = r[SCOPES.ID]!!,
                    ownerId = r[SCOPES.OWNER_ID]!!,
                    handle = r[USERS.HANDLE]!!,
                    displayName = r[USERS.DISPLAY_NAME]!!,
                    avatarSeed = r[USERS.AVATAR_SEED]!!,
                    memberSince = r[USERS.CREATED_AT]!!.toInstant(),
                )
            }

    fun viewersOf(ownerId: UUID): List<Subscription> =
        dsl.select(SCOPES.ID, USERS.ID, USERS.HANDLE, USERS.DISPLAY_NAME, USERS.AVATAR_SEED, USERS.CREATED_AT)
            .from(SCOPE_MEMBERS)
            .join(SCOPES).on(SCOPES.ID.eq(SCOPE_MEMBERS.SCOPE_ID))
            .join(USERS).on(USERS.ID.eq(SCOPE_MEMBERS.USER_ID))
            .where(SCOPES.OWNER_ID.eq(ownerId))
            .orderBy(USERS.DISPLAY_NAME.asc())
            .fetch { r ->
                Subscription(
                    scopeId = r[SCOPES.ID]!!,
                    ownerId = r[USERS.ID]!!,
                    handle = r[USERS.HANDLE]!!,
                    displayName = r[USERS.DISPLAY_NAME]!!,
                    avatarSeed = r[USERS.AVATAR_SEED]!!,
                    memberSince = r[USERS.CREATED_AT]!!.toInstant(),
                )
            }

    fun share(ownerId: UUID, viewerId: UUID) {
        dsl.insertInto(SCOPE_MEMBERS)
            .set(SCOPE_MEMBERS.SCOPE_ID, ensureBoardOf(ownerId))
            .set(SCOPE_MEMBERS.USER_ID, viewerId)
            .onConflictDoNothing()
            .execute()
    }

    fun unshare(ownerId: UUID, viewerId: UUID) {
        val boardId = boardOf(ownerId) ?: return
        dsl.deleteFrom(SCOPE_MEMBERS)
            .where(SCOPE_MEMBERS.SCOPE_ID.eq(boardId), SCOPE_MEMBERS.USER_ID.eq(viewerId))
            .execute()
    }

    fun requireBoardOwner(ownerId: UUID, viewerId: UUID) {
        if (ownerId != viewerId) {
            requireResident(ownerId)
            throw ApiException(HttpStatus.FORBIDDEN, ProblemCode.SCOPE_FORBIDDEN)
        }
    }

    fun requireSharedBoard(scopeId: UUID) {
        if (!isGlobal(scopeId)) {
            throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)
        }
    }

    fun requireAllowsType(kind: ScopeKind, typeKey: String) {
        if (kind.types.none { it.key == typeKey }) {
            throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.TYPE_NOT_IN_SCOPE)
        }
    }

    private fun requireResident(ownerId: UUID) {
        if (!dsl.fetchExists(USERS, USERS.ID.eq(ownerId))) {
            throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)
        }
    }
}
