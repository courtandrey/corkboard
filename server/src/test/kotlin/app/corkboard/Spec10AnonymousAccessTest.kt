package app.corkboard

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod

// Spec §14.1 e2e #10: the anonymous access matrix — public reads stay readable,
// every authenticated route answers 401 problem+json. Grows with each milestone.
class Spec10AnonymousAccessTest : ApiTestBase() {

    @Test
    fun `public reads are open to anonymous visitors`() {
        assertThat(rest.getForEntity("/api/v1/health", String::class.java).statusCode.value()).isEqualTo(200)
        assertThat(rest.getForEntity("/api/v1/meta", String::class.java).statusCode.value()).isEqualTo(200)
        assertThat(rest.getForEntity("/api/v1/openapi.json", String::class.java).statusCode.value()).isEqualTo(200)
    }

    @Test
    fun `authenticated routes answer 401 problem json`() {
        val routes = listOf(
            HttpMethod.GET to "/api/v1/auth/me",
            HttpMethod.POST to "/api/v1/auth/logout",
        )
        for ((method, path) in routes) {
            val res = rest.exchange(path, method, HttpEntity.EMPTY, String::class.java)
            assertThat(res.statusCode.value()).describedAs("%s %s", method, path).isEqualTo(401)
            assertThat(res.headers.contentType.toString()).contains("application/problem+json")
            assertThat(res.body).contains("\"code\":\"unauthenticated\"")
        }
    }

    @Test
    fun `google login is absent while the flag is off`() {
        val res = rest.getForEntity("/api/v1/auth/google", String::class.java)
        assertThat(res.statusCode.value()).isEqualTo(404)
    }
}
