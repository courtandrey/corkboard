package app.corkboard

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class MutableClock(
    @Volatile private var current: Instant,
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock() {

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)

    override fun instant(): Instant = current

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }
}

@TestConfiguration
class MutableClockConfig {

    @Bean
    @Primary
    fun mutableClock(): MutableClock = MutableClock(Instant.now())
}
