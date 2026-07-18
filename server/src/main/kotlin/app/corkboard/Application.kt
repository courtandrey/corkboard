package app.corkboard

import app.corkboard.common.CorkboardProperties
import java.time.Clock
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableScheduling
@Profile("!seed")
class SchedulingConfig

@SpringBootApplication
@EnableConfigurationProperties(CorkboardProperties::class)
class Application {

    @Bean
    fun clock(): Clock = Clock.systemUTC()
}

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
