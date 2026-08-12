package app.corkboard.auth

import app.corkboard.common.ApiException
import app.corkboard.common.ProblemCode
import app.corkboard.jooq.tables.references.ROLES
import app.corkboard.jooq.tables.references.USERS
import app.corkboard.jooq.tables.references.USER_ROLES
import java.util.UUID
import org.jooq.DSLContext
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

@Service
class UserRoleService(
    private val dsl: DSLContext,
    private val catalog: RoleCatalog,
) {

    fun rolesOf(userId: UUID, emailVerified: Boolean): Set<String> = buildSet {
        add(Roles.RESIDENT)
        if (emailVerified) add(Roles.VERIFIED_RESIDENT)
        addAll(grantedTo(userId))
    }

    fun permissionsOf(userId: UUID, emailVerified: Boolean): Set<Permission> =
        catalog.permissionsOf(rolesOf(userId, emailVerified))

    fun grant(userId: UUID, roleKey: String, grantedBy: UUID) {
        val roleId = roleIdOf(roleKey)
        requireUser(userId)
        dsl.insertInto(USER_ROLES)
            .set(USER_ROLES.USER_ID, userId)
            .set(USER_ROLES.ROLE_ID, roleId)
            .set(USER_ROLES.GRANTED_BY, grantedBy)
            .onConflictDoNothing()
            .execute()
    }

    fun revoke(userId: UUID, roleKey: String) {
        val roleId = roleIdOf(roleKey)
        dsl.deleteFrom(USER_ROLES)
            .where(USER_ROLES.USER_ID.eq(userId), USER_ROLES.ROLE_ID.eq(roleId))
            .execute()
    }

    private fun grantedTo(userId: UUID): List<String> =
        dsl.select(ROLES.KEY)
            .from(USER_ROLES)
            .join(ROLES).on(ROLES.ID.eq(USER_ROLES.ROLE_ID))
            .where(USER_ROLES.USER_ID.eq(userId))
            .fetch(ROLES.KEY)
            .filterNotNull()

    private fun roleIdOf(roleKey: String): Int {
        if (roleKey == Roles.RESIDENT || roleKey == Roles.VERIFIED_RESIDENT) {
            throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.ROLE_NOT_GRANTABLE)
        }
        return dsl.select(ROLES.ID).from(ROLES).where(ROLES.KEY.eq(roleKey)).fetchOne(ROLES.ID)
            ?: throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)
    }

    private fun requireUser(userId: UUID) {
        val exists = dsl.fetchExists(USERS, USERS.ID.eq(userId))
        if (!exists) throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)
    }
}
