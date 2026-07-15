package app.corkboard.common

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

data class HealthResponse(val status: String)

@RestController
class HealthController(private val jdbc: JdbcClient) {

    @GetMapping("/api/v1/health")
    fun health(): HealthResponse {
        jdbc.sql("SELECT 1").query(Int::class.java).single()
        return HealthResponse(status = "ok")
    }
}
