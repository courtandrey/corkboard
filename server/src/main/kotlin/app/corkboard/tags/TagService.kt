package app.corkboard.tags

import app.corkboard.common.ApiException
import app.corkboard.common.ProblemCode
import app.corkboard.jooq.tables.references.EVENT_TAGS
import app.corkboard.jooq.tables.references.TAGS
import java.util.UUID
import org.jooq.DSLContext
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

@Service
class TagService(private val dsl: DSLContext) {

    fun replaceEventTags(eventId: UUID, names: List<String>) {
        val tagIds = ensure(names)
        dsl.deleteFrom(EVENT_TAGS).where(EVENT_TAGS.EVENT_ID.eq(eventId)).execute()
        tagIds.forEach { tagId ->
            dsl.insertInto(EVENT_TAGS)
                .set(EVENT_TAGS.EVENT_ID, eventId)
                .set(EVENT_TAGS.TAG_ID, tagId)
                .execute()
        }
    }

    fun eventTags(eventId: UUID): List<Pair<String, String>> =
        dsl.select(TAGS.NAME, TAGS.SLUG)
            .from(TAGS)
            .join(EVENT_TAGS).on(EVENT_TAGS.TAG_ID.eq(TAGS.ID))
            .where(EVENT_TAGS.EVENT_ID.eq(eventId))
            .orderBy(TAGS.SLUG)
            .fetch { it[TAGS.NAME]!! to it[TAGS.SLUG]!! }

    private fun ensure(names: List<String>): List<Int> {
        val normalized = LinkedHashMap<String, String>()
        for (raw in names) {
            val name = raw.trim().replace(Regex("\\s+"), " ")
            val slug = slugify(name)
            if (name.length !in 2..40 || slug.isEmpty()) {
                throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.VALIDATION_FAILED)
            }
            normalized.putIfAbsent(slug, name)
        }
        return normalized.map { (slug, name) -> findOrCreate(slug, name) }
    }

    private fun findOrCreate(slug: String, name: String): Int {
        val existing = dsl.select(TAGS.ID).from(TAGS).where(TAGS.SLUG.eq(slug)).fetchOne(TAGS.ID)
        if (existing != null) return existing
        dsl.insertInto(TAGS)
            .set(TAGS.NAME, name)
            .set(TAGS.SLUG, slug)
            .onConflictDoNothing()
            .execute()
        return dsl.select(TAGS.ID).from(TAGS).where(TAGS.SLUG.eq(slug)).fetchOne(TAGS.ID)!!
    }

    companion object {
        fun slugify(name: String): String =
            name.lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .take(40)
                .trimEnd('-')
    }
}
