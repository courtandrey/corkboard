package app.corkboard.notifier.templates

import app.corkboard.notifier.jooq.tables.references.EMAIL_TEMPLATES
import java.util.concurrent.ConcurrentHashMap
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository

data class EmailTemplate(
    val type: String,
    val version: Int,
    val subject: String,
    val html: String,
    val text: String,
    val variables: Set<String>,
)

class MissingTemplateException(type: String) : RuntimeException("no template for $type")

@Repository
class EmailTemplates(private val dsl: DSLContext) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val cache = ConcurrentHashMap<String, EmailTemplate>()

    fun latest(type: String): EmailTemplate = cache.computeIfAbsent(type) { load(it) }

    private fun load(type: String): EmailTemplate {
        val row = dsl.select(
            EMAIL_TEMPLATES.TYPE,
            EMAIL_TEMPLATES.VERSION,
            EMAIL_TEMPLATES.SUBJECT,
            EMAIL_TEMPLATES.HTML,
            EMAIL_TEMPLATES.TEXT_BODY,
            EMAIL_TEMPLATES.VARIABLES,
        )
            .from(EMAIL_TEMPLATES)
            .where(EMAIL_TEMPLATES.TYPE.eq(type))
            .orderBy(EMAIL_TEMPLATES.VERSION.desc())
            .limit(1)
            .fetchOne()
            ?: throw MissingTemplateException(type)

        val template = EmailTemplate(
            type = row[EMAIL_TEMPLATES.TYPE]!!,
            version = row[EMAIL_TEMPLATES.VERSION]!!,
            subject = row[EMAIL_TEMPLATES.SUBJECT]!!,
            html = row[EMAIL_TEMPLATES.HTML]!!,
            text = row[EMAIL_TEMPLATES.TEXT_BODY]!!,
            variables = row[EMAIL_TEMPLATES.VARIABLES]!!.filterNotNull().toSet(),
        )
        log.info("loaded template {} v{} ({} variables)", template.type, template.version, template.variables.size)
        return template
    }
}
