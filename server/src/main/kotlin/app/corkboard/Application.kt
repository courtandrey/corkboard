package app.corkboard

import app.corkboard.common.CorkboardProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(CorkboardProperties::class)
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
