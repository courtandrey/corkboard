package app.corkboard.notifier

import app.corkboard.notifier.config.NotifierProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(NotifierProperties::class)
class NotifierApplication

fun main(args: Array<String>) {
    runApplication<NotifierApplication>(*args)
}
