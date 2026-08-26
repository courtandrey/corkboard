package app.corkboard.auth

import app.corkboard.ApiTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

class WriteEndpointsAreGuardedTest : ApiTestBase() {

    @Autowired
    lateinit var mappings: RequestMappingHandlerMapping

    private val exempt = mapOf(
        "POST /api/v1/auth/register" to "signing up cannot require an account",
        "POST /api/v1/auth/login" to "signing in cannot require an account",
        "POST /api/v1/auth/google/complete" to "finishing a sign-up is what creates the account",
        "POST /api/v1/auth/logout" to "leaving is always allowed",
        "PATCH /api/v1/auth/me" to "renaming yourself reaches nobody else",
        "POST /api/v1/auth/verification/resend" to "the way out of being unconfirmed",
        "POST /api/v1/conversations/{id}/read" to "marks your own view of a thread",
        "POST /api/v1/notifications/read" to "clears your own bell",
    )

    private val writeMethods = setOf(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)

    private fun patternsOf(info: org.springframework.web.servlet.mvc.method.RequestMappingInfo): Set<String> =
        info.pathPatternsCondition?.patternValues
            ?: info.patternsCondition?.patterns.orEmpty().toSet()

    @Test
    fun `every write endpoint names the permission it needs`() {
        val unguarded = mappings.handlerMethods
            .filterKeys { info ->
                patternsOf(info).any { it.startsWith("/api/v1/") } &&
                    info.methodsCondition.methods.any { HttpMethod.valueOf(it.name) in writeMethods }
            }
            .filterValues { handler ->
                handler.getMethodAnnotation(PreAuthorize::class.java) == null &&
                    handler.beanType.getAnnotation(PreAuthorize::class.java) == null
            }
            .keys
            .flatMap { info ->
                info.methodsCondition.methods.flatMap { method ->
                    patternsOf(info).map { "${method.name} $it" }
                }
            }
            .filter { it !in exempt }
            .sorted()

        assertThat(unguarded)
            .describedAs("add @PreAuthorize, or list it in `exempt` above with the reason it needs none")
            .isEmpty()
    }
}
