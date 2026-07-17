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
        val bounds: Bounds,
        val zoom: Int,
        val types: List<EventType>?,
        val tagSlugs: List<String>?,
        val applyable: Boolean?,
        val q: String?,
        val viewerId: UUID?,
        val limit: Int,
    )

    fun run(p: Params): ViewportResponse {
        val truncated = p.bounds.west > p.bounds.east
        val east = if (truncated) 180.0 else p.bounds.east
        val cell = ((east - p.bounds.west) / 12.0).coerceAtLeast(1e-9)
        val perCell = if (p.zoom <= 13) 3 else 6

        val cond = conditions(p, east)

        val lng = DSL.field("ST_X({0})", Double::class.java, EVENTS.LOCATION).`as`("lng")
        val lat = DSL.field("ST_Y({0})", Double::class.java, EVENTS.LOCATION).`as`("lat")
        val cellField = DSL.field(
            "ST_SnapToGrid({0}, {1}, {2})",
            Any::class.java, EVENTS.LOCATION, DSL.`val`(cell), DSL.`val`(cell),
        ).`as`("cell")

        val candidates = DSL.select(
            EVENTS.ID, EVENTS.TYPE, EVENTS.TITLE, EVENTS.APPLYABLE, EVENTS.SCORE,
            EVENTS.APPLICATION_COUNT, EVENTS.EXPIRES_AT, EVENTS.CREATED_AT,
            lng, lat, cellField,
        ).from(EVENTS).where(cond).asTable("candidates")

        val cellRank = DSL.rowNumber().over(
            DSL.partitionBy(candidates.field("cell"))
                .orderBy(candidates.field(EVENTS.SCORE)!!.desc(), candidates.field(EVENTS.CREATED_AT)!!.desc())
        ).`as`("cell_rank")

        val ranked = DSL.select(candidates.asterisk(), cellRank).from(candidates).asTable("ranked")

        val idF = ranked.field(EVENTS.ID)!!
        val typeF = ranked.field(EVENTS.TYPE)!!
        val titleF = ranked.field(EVENTS.TITLE)!!
        val applyableF = ranked.field(EVENTS.APPLYABLE)!!
        val scoreF = ranked.field(EVENTS.SCORE)!!
        val appCountF = ranked.field(EVENTS.APPLICATION_COUNT)!!
        val expiresF = ranked.field(EVENTS.EXPIRES_AT)!!
        val createdF = ranked.field(EVENTS.CREATED_AT)!!
        val lngF = ranked.field("lng", Double::class.java)!!
        val latF = ranked.field("lat", Double::class.java)!!

        val items = dsl.select(idF, typeF, titleF, applyableF, scoreF, appCountF, expiresF, createdF, lngF, latF)
            .from(ranked)
            .where(ranked.field("cell_rank", Int::class.java)!!.le(perCell))
            .orderBy(scoreF.desc(), createdF.desc())
            .limit(p.limit)
            .fetch { r ->
                EventPin(
                    id = r[idF]!!,
                    type = EventType.fromKey(r[typeF]!!.literal)!!,
                    title = r[titleF]!!,
                    location = LatLng(r[lngF]!!, r[latF]!!),
                    applyable = r[applyableF]!!,
                    score = r[scoreF]!!,
                    applicationCount = r[appCountF]!!,
                    expiresAt = r[expiresF]!!.toInstant(),
                    createdAt = r[createdF]!!.toInstant(),
                )
            }

        val total = dsl.fetchCount(DSL.selectOne().from(EVENTS).where(cond).limit(500))
        return ViewportResponse(items, total, truncated)
    }

    private fun conditions(p: Params, east: Double): Condition {
        val now = OffsetDateTime.now(clock)
        var cond = DSL.condition(
            "{0} && ST_MakeEnvelope({1}, {2}, {3}, {4}, 4326)",
            EVENTS.LOCATION, DSL.`val`(p.bounds.west), DSL.`val`(p.bounds.south),
            DSL.`val`(east), DSL.`val`(p.bounds.north),
        )
            .and(
                EVENTS.STATUS.eq(DbEventStatus.active).and(EVENTS.EXPIRES_AT.gt(now))
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
