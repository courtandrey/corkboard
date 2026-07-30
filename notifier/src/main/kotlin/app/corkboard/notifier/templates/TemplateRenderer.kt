package app.corkboard.notifier.templates

import org.springframework.stereotype.Component

data class RenderedEmail(val subject: String, val html: String, val text: String)

class MissingVariableException(type: String, missing: Set<String>) :
    RuntimeException("template $type needs ${missing.sorted().joinToString(", ")}")

@Component
class TemplateRenderer {

    fun render(template: EmailTemplate, variables: Map<String, String>): RenderedEmail {
        val missing = template.variables - variables.keys
        if (missing.isNotEmpty()) throw MissingVariableException(template.type, missing)

        return RenderedEmail(
            subject = substitute(template.subject, template.variables, variables, escape = false),
            html = substitute(template.html, template.variables, variables, escape = true),
            text = substitute(template.text, template.variables, variables, escape = false),
        )
    }

    private fun substitute(
        copy: String,
        declared: Set<String>,
        values: Map<String, String>,
        escape: Boolean,
    ): String =
        PLACEHOLDER.replace(copy) { match ->
            val name = match.groupValues[1]
            if (name !in declared) {
                match.value
            } else {
                values.getValue(name).let { if (escape) escapeHtml(it) else it }
            }
        }

    private fun escapeHtml(value: String): String {
        val out = StringBuilder(value.length + 16)
        for (character in value) {
            when (character) {
                '&' -> out.append("&amp;")
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")
                '"' -> out.append("&quot;")
                '\'' -> out.append("&#39;")
                else -> out.append(character)
            }
        }
        return out.toString()
    }

    private companion object {
        val PLACEHOLDER = Regex("@([a-z][a-z0-9_]*)")
    }
}
