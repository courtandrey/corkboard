package app.corkboard.notifier

import app.corkboard.notifier.templates.EmailTemplate
import app.corkboard.notifier.templates.MissingVariableException
import app.corkboard.notifier.templates.TemplateRenderer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class TemplateRendererTest {

    private val renderer = TemplateRenderer()

    private fun template(
        subject: String = "Hello @user_name",
        html: String = "<p>Hi @user_name, <a href=\"@verification_link\">confirm</a></p>",
        text: String = "Hi @user_name, open @verification_link",
        variables: Set<String> = setOf("user_name", "verification_link"),
    ) = EmailTemplate("EMAIL_VERIFICATION", 1, subject, html, text, variables)

    @Test
    fun `values land in subject, html and text`() {
        val rendered = renderer.render(
            template(),
            mapOf("user_name" to "Marisol", "verification_link" to "https://board.example.com/verify?token=abc"),
        )

        assertThat(rendered.subject).isEqualTo("Hello Marisol")
        assertThat(rendered.text).isEqualTo("Hi Marisol, open https://board.example.com/verify?token=abc")
        assertThat(rendered.html).contains("Hi Marisol")
    }

    @Test
    fun `html values are escaped, plain text is left alone`() {
        val rendered = renderer.render(
            template(),
            mapOf(
                "user_name" to "Mar<script>alert(1)</script>",
                "verification_link" to "https://board.example.com/verify?token=a&b=c",
            ),
        )

        assertThat(rendered.html).doesNotContain("<script>")
        assertThat(rendered.html).contains("&lt;script&gt;")
        assertThat(rendered.html)
            .describedAs("an ampersand inside an attribute has to be an entity")
            .contains("token=a&amp;b=c")
        assertThat(rendered.text)
            .describedAs("nothing to escape in a plain body")
            .contains("token=a&b=c")
    }

    @Test
    fun `an address in the copy is not mistaken for a placeholder`() {
        val rendered = renderer.render(
            template(text = "Write to keepers@lamppostal.test or open @verification_link", subject = "Hi"),
            mapOf("user_name" to "Marisol", "verification_link" to "https://board.example.com/verify"),
        )

        assertThat(rendered.text).contains("keepers@lamppostal.test")
    }

    @Test
    fun `a template whose variables were not supplied is refused`() {
        assertThatThrownBy {
            renderer.render(template(), mapOf("user_name" to "Marisol"))
        }
            .isInstanceOf(MissingVariableException::class.java)
            .hasMessageContaining("verification_link")
    }

    @Test
    fun `a substituted value that looks like a placeholder is not rewritten`() {
        val rendered = renderer.render(
            template(subject = "Hello @user_name", text = "@user_name", html = "<p>@user_name</p>"),
            mapOf("user_name" to "@verification_link", "verification_link" to "https://board.example.com/verify"),
        )

        assertThat(rendered.text).isEqualTo("@verification_link")
    }
}
