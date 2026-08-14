package app.corkboard.scopes

import app.corkboard.common.ApiException
import app.corkboard.common.ProblemCode
import app.corkboard.features.FeatureFlag
import app.corkboard.features.FeatureFlagService
import app.corkboard.jooq.enums.ScopeKind as DbScopeKind
import app.corkboard.jooq.tables.references.SCOPES
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
        if (ownerId != viewerId) {
            requireResident(ownerId)
            throw ApiException(HttpStatus.FORBIDDEN, ProblemCode.SCOPE_FORBIDDEN)
        }
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
