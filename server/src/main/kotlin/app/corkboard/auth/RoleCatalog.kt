package app.corkboard.auth

import app.corkboard.jooq.tables.references.ROLES
import app.corkboard.jooq.tables.references.ROLE_PERMISSIONS
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class RoleCatalog(private val dsl: DSLContext) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val byRole: Map<String, Set<Permission>> by lazy { load() }

    fun permissionsOf(roleKeys: Collection<String>): Set<Permission> =
        roleKeys.flatMapTo(mutableSetOf()) { byRole[it].orEmpty() }

    fun knows(roleKey: String): Boolean = byRole.containsKey(roleKey)

    fun roleKeys(): Set<String> = byRole.keys

    private fun load(): Map<String, Set<Permission>> {
        val rows = dsl.select(ROLES.KEY, ROLE_PERMISSIONS.PERMISSION)
            .from(ROLES)
            .leftJoin(ROLE_PERMISSIONS).on(ROLE_PERMISSIONS.ROLE_ID.eq(ROLES.ID))
            .fetch()

        val unknown = rows.mapNotNull { it[ROLE_PERMISSIONS.PERMISSION] }.filter { Permission.of(it) == null }
        if (unknown.isNotEmpty()) {
            log.warn("ignoring permissions this build does not know: {}", unknown.distinct())
        }

        return rows.groupBy({ it[ROLES.KEY]!! }) { it[ROLE_PERMISSIONS.PERMISSION] }
            .mapValues { (_, permissions) -> permissions.mapNotNull { it?.let(Permission::of) }.toSet() }
    }
}
