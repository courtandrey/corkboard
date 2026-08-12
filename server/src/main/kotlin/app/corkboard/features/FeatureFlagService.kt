package app.corkboard.features

import app.corkboard.jooq.tables.references.FEATURE_FLAGS
import app.corkboard.jooq.tables.references.USERS
import jakarta.annotation.PostConstruct
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

data class FeatureFlagState(
    val flag: FeatureFlag,
    val enabled: Boolean,
    val updatedAt: Instant?,
    val updatedBy: UUID?,
)

data class FeatureFlagChange(
    val flag: FeatureFlag,
    val enabled: Boolean,
    val updatedAt: Instant?,
    val updatedByName: String?,
)

data class FeatureFlagsChanged(val flags: Map<String, Boolean>)

@Service
class FeatureFlagService(
    private val dsl: DSLContext,
    private val events: ApplicationEventPublisher,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val snapshot = AtomicReference(defaults())

    @PostConstruct
    fun load() {
        refresh()
    }

    fun isEnabled(flag: FeatureFlag): Boolean = snapshot.get().getValue(flag).enabled

    fun enabledByKey(): Map<String, Boolean> =
        snapshot.get().values.sortedBy { it.flag.name }.associate { it.flag.name to it.enabled }

    fun states(): List<FeatureFlagState> = snapshot.get().values.sortedBy { it.flag.name }

    fun set(flag: FeatureFlag, enabled: Boolean, actor: UUID): FeatureFlagState {
        dsl.insertInto(FEATURE_FLAGS)
            .set(FEATURE_FLAGS.KEY, flag.name)
            .set(FEATURE_FLAGS.ENABLED, enabled)
            .set(FEATURE_FLAGS.UPDATED_BY, actor)
            .onConflict(FEATURE_FLAGS.KEY)
            .doUpdate()
            .set(FEATURE_FLAGS.ENABLED, enabled)
            .set(FEATURE_FLAGS.UPDATED_AT, DSL.currentOffsetDateTime())
            .set(FEATURE_FLAGS.UPDATED_BY, actor)
            .execute()

        refresh()
        announce(flag)
        return snapshot.get().getValue(flag)
    }

    fun changes(): List<FeatureFlagChange> {
        val names = dsl.select(FEATURE_FLAGS.KEY, USERS.DISPLAY_NAME)
            .from(FEATURE_FLAGS)
            .leftJoin(USERS).on(USERS.ID.eq(FEATURE_FLAGS.UPDATED_BY))
            .fetch()
            .associate { it[FEATURE_FLAGS.KEY] to it[USERS.DISPLAY_NAME] }

        return states().map { FeatureFlagChange(it.flag, it.enabled, it.updatedAt, names[it.flag.name]) }
    }

    fun refresh() {
        val loaded = read()
        if (snapshot.getAndSet(loaded) != loaded) {
            events.publishEvent(FeatureFlagsChanged(enabledByKey()))
        }
    }

    private fun read(): Map<FeatureFlag, FeatureFlagState> {
        val rows = dsl.selectFrom(FEATURE_FLAGS).fetch()

        val unknown = rows.map { it[FEATURE_FLAGS.KEY]!! }.filter { FeatureFlag.of(it) == null }
        if (unknown.isNotEmpty()) {
            log.warn("ignoring feature flags this build does not know: {}", unknown)
        }

        val stored = rows.mapNotNull { row ->
            FeatureFlag.of(row[FEATURE_FLAGS.KEY]!!)?.let {
                it to FeatureFlagState(
                    it,
                    row[FEATURE_FLAGS.ENABLED]!!,
                    row[FEATURE_FLAGS.UPDATED_AT]!!.toInstant(),
                    row[FEATURE_FLAGS.UPDATED_BY],
                )
            }
        }.toMap()

        return defaults() + stored
    }

    private fun defaults(): Map<FeatureFlag, FeatureFlagState> =
        FeatureFlag.entries.associateWith { FeatureFlagState(it, it.defaultEnabled, null, null) }

    private fun announce(flag: FeatureFlag) {
        runCatching {
            dsl.select(
                DSL.function(
                    "pg_notify",
                    String::class.java,
                    DSL.`val`(FeatureFlagBus.CHANNEL),
                    DSL.`val`(flag.name),
                ),
            ).execute()
        }.onFailure { log.warn("could not announce the change to {} — other instances will catch up", flag, it) }
    }
}
