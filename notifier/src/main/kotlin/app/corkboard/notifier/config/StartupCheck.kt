package app.corkboard.notifier.config

import app.corkboard.notifier.mail.EmailDispatcher
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

const val DEV_API_KEY = "dev-notifier-key"

@Component
class StartupCheck(
    private val props: NotifierProperties,
    private val dispatcher: EmailDispatcher,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun report() {
        log.info(
            "notifier ready — transport={}, from={}",
            dispatcher.transport,
            props.from.ifBlank { "(unset)" },
        )
        if (props.apiKey == DEV_API_KEY) {
            log.warn("NOTIFIER_API_KEY is the development default — set a real key before this service leaves your machine")
        }
    }
}
