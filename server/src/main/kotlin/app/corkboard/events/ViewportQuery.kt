package app.corkboard.events

import app.corkboard.jooq.enums.EventStatus as DbEventStatus
import app.corkboard.jooq.enums.EventType as DbEventType
import app.corkboard.jooq.tables.references.EVENTS
import app.corkboard.jooq.tables.references.EVENT_HIDES
import app.corkboard.jooq.tables.references.EVENT_TAGS
import app.corkboard.jooq.tables.references.TAGS
import app.corkboard.meta.EventType
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Component

@Component
class ViewportQuery(
    private val dsl: DSLContext,
    private val clock: Clock,
) {

    data class Params(
        val scopeId: UUID,
        val bounds: Bounds,
        val zoom: Int,
        val types: List<EventType>?,
        val tagSlugs: List<String>?,
        val applyable: Boolean?,
        val q: String?,
        val viewerId: UUID?,
        val limit: Int,
        val clustered: Boolean = true,
    )

    companion object {
        fun cellSizeDeg(zoom: Int): Double = 45.0 / (1 shl zoom.coerceIn(0, 20))

        const val MAX_CELLS_PER_AXIS = 45

        private val LNG = DSL.field("ST_X({0})", Double::class.java, EVENTS.LOCATION)
        private val LAT = DSL.field("ST_Y({0})", Double::class.java, EVENTS.LOCATION)
    }

    fun run(p: Params): ViewportResponse {
        if (!p.clustered) {
            return unclustered(p)
        }

        val wrapped = p.bounds.west > p.bounds.east
        val lngSpan = if (wrapped) (180.0 - p.bounds.west) + (p.bounds.east + 180.0) else p.bounds.east - p.bounds.west
        val latSpan = p.bounds.north - p.bounds.south
        var zoom = p.zoom.coerceIn(0, 20)
        while (zoom > 0 &&
            (lngSpan / cellSizeDeg(zoom) > MAX_CELLS_PER_AXIS || latSpan / cellSizeDeg(zoom) > MAX_CELLS_PER_AXIS)
        ) {
            zoom--
        }
        val cell = cellSizeDeg(zoom)
        val cond = conditions(p, cell)

        val cellX = DSL.field("floor(ST_X({0}) / {1})", Double::class.java, EVENTS.LOCATION, DSL.`val`(cell))
        val cellY = DSL.field("floor(ST_Y({0}) / {1})", Double::class.java, EVENTS.LOCATION, DSL.`val`(cell))
        val n = DSL.count()
        val avgLng = DSL.avg(LNG)
        val avgLat = DSL.avg(LAT)
        val minLng = DSL.min(LNG)
        val maxLng = DSL.max(LNG)
        val minLat = DSL.min(LAT)
        val maxLat = DSL.max(LAT)
        val anyId = DSL.field("min({0}::text)", String::class.java, EVENTS.ID)

        val cells = dsl.select(n, avgLng, avgLat, minLng, maxLng, minLat, maxLat, anyId)
            .from(EVENTS)
            .where(cond)
            .groupBy(cellX, cellY)
            .fetch()

        val total = cells.sumOf { it[n]!! }
        val singleIds = cells.filter { it[n] == 1 }.map { UUID.fromString(it[anyId]!!) }
        val clusters = cells.filter { it[n]!! > 1 }
            .map { r ->
                ClusterPin(
                    count = r[n]!!,
                    location = LatLng(r[avgLng]!!.toDouble(), r[avgLat]!!.toDouble()),
                    bounds = ClusterBounds(
                        west = r[minLng]!!,
                        south = r[minLat]!!,
                        east = r[maxLng]!!,
                        north = r[maxLat]!!,
                    ),
                )
            }
            .sortedByDescending { it.count }

        return ViewportResponse(fetchPins(singleIds, null), clusters, total)
    }

    private fun unclustered(p: Params): ViewportResponse {
        val cond = conditions(p, cell = null)
        val items = fetchPins(null, cond, p.limit)
        val total = dsl.fetchCount(DSL.selectOne().from(EVENTS).where(cond).limit(500))
        return ViewportResponse(items, emptyList(), total)
    }

    private fun fetchPins(ids: List<UUID>?, cond: Condition?, limit: Int? = null): List<EventPin> {
        if (ids != null && ids.isEmpty()) return emptyList()
        val where = cond ?: EVENTS.ID.`in`(ids)
        val step = dsl.select(
            EVENTS.ID, EVENTS.TYPE, EVENTS.TITLE, EVENTS.APPLYABLE, EVENTS.SCORE,
            EVENTS.APPLICATION_COUNT, EVENTS.EXPIRES_AT, EVENTS.CREATED_AT, LNG, LAT,
        )
            .from(EVENTS)
            .where(where)
            .orderBy(EVENTS.SCORE.desc(), EVENTS.CREATED_AT.desc())
        return (limit?.let { step.limit(it) } ?: step).fetch { r ->
            EventPin(
                id = r[EVENTS.ID]!!,
                type = EventType.fromKey(r[EVENTS.TYPE]!!.literal)!!,
                title = r[EVENTS.TITLE]!!,
                location = LatLng(r[LNG]!!, r[LAT]!!),
                applyable = r[EVENTS.APPLYABLE]!!,
                score = r[EVENTS.SCORE]!!,
                applicationCount = r[EVENTS.APPLICATION_COUNT]!!,
                expiresAt = r[EVENTS.EXPIRES_AT]?.toInstant(),
                createdAt = r[EVENTS.CREATED_AT]!!.toInstant(),
            )
        }
    }

    private fun envelope(west: Double, south: Double, east: Double, north: Double): Condition =
        DSL.condition(
            "{0} && ST_MakeEnvelope({1}, {2}, {3}, {4}, 4326)",
            EVENTS.LOCATION, DSL.`val`(west), DSL.`val`(south),
            DSL.`val`(east), DSL.`val`(north),
        )

    private fun bboxCondition(bounds: Bounds, cell: Double?): Condition {
        val snapDown = { v: Double -> if (cell == null) v else floor(v / cell) * cell }
        val snapUp = { v: Double -> if (cell == null) v else ceil(v / cell) * cell }
        val south = snapDown(bounds.south).coerceAtLeast(-85.05)
        val north = snapUp(bounds.north).coerceAtMost(85.05)
        val wrapped = bounds.west > bounds.east
        if (!wrapped) {
            return envelope(snapDown(bounds.west).coerceAtLeast(-180.0), south, snapUp(bounds.east).coerceAtMost(180.0), north)
        }
        return envelope(snapDown(bounds.west), south, 180.0, north)
            .or(envelope(-180.0, south, snapUp(bounds.east), north))
    }

    private fun conditions(p: Params, cell: Double?): Condition {
        val now = OffsetDateTime.now(clock)
        var cond = EVENTS.SCOPE_ID.eq(p.scopeId)
            .and(bboxCondition(p.bounds, cell))
            .and(
                EVENTS.STATUS.eq(DbEventStatus.active)
                    .and(EVENTS.EXPIRES_AT.isNull.or(EVENTS.EXPIRES_AT.gt(now)))
                    .or(
                        EVENTS.STATUS.eq(DbEventStatus.resolved)
                            .and(EVENTS.RESOLVED_AT.gt(now.minusHours(48)))
                    )
            )

        p.types?.takeIf { it.isNotEmpty() }?.let { types ->
            cond = cond.and(EVENTS.TYPE.`in`(types.map { DbEventType.valueOf(it.key) }))
        }
        p.applyable?.let { cond = cond.and(EVENTS.APPLYABLE.eq(it)) }
        p.viewerId?.let { viewer ->
            cond = cond.and(
                DSL.notExists(
                    DSL.selectOne().from(EVENT_HIDES)
                        .where(EVENT_HIDES.USER_ID.eq(viewer), EVENT_HIDES.EVENT_ID.eq(EVENTS.ID))
                )
            )
        }
        p.tagSlugs?.takeIf { it.isNotEmpty() }?.let { slugs ->
            cond = cond.and(
                DSL.exists(
                    DSL.selectOne().from(EVENT_TAGS)
                        .join(TAGS).on(TAGS.ID.eq(EVENT_TAGS.TAG_ID))
                        .where(EVENT_TAGS.EVENT_ID.eq(EVENTS.ID), TAGS.SLUG.`in`(slugs))
                )
            )
        }
        p.q?.takeIf { it.isNotBlank() }?.let { q ->
            cond = cond.and(EVENTS.TITLE.containsIgnoreCase(q).or(EVENTS.BODY.containsIgnoreCase(q)))
        }
        return cond
    }
}
