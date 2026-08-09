package app.corkboard.common

import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RateLimiterTest {

    private val time = AtomicLong(0)
    private val limiter = RateLimiter(
        permitsPerMinute = 1,
        maxIdle = Duration.ofMinutes(2),
        pruneAt = 2,
        ticker = time::get,
    )

    @Test
    fun `keys are limited independently`() {
        assertThat(limiter.tryConsume("a")).isTrue()
        assertThat(limiter.tryConsume("a")).describedAs("a's single permit is spent").isFalse()
        assertThat(limiter.tryConsume("b")).describedAs("b is untouched by a").isTrue()
    }

    @Test
    fun `idle buckets are dropped once the map grows, active ones survive with their debt`() {
        limiter.tryConsume("a")
        limiter.tryConsume("b")
        limiter.tryConsume("c")

        time.set(Duration.ofMinutes(3).toNanos())
        limiter.tryConsume("d")

        assertThat(limiter.tryConsume("a"))
            .describedAs("a spent bucket answering true again proves it was dropped and recreated full")
            .isTrue()

        assertThat(limiter.tryConsume("e")).isTrue()
        assertThat(limiter.tryConsume("e")).isFalse()
        time.set(Duration.ofMinutes(4).toNanos())
        limiter.tryConsume("f")
        limiter.tryConsume("g")
        limiter.tryConsume("h")
        assertThat(limiter.tryConsume("e"))
            .describedAs("e was used one minute ago — inside maxIdle — so its spent state survives")
            .isFalse()
    }
}
