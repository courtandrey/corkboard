package app.corkboard.notifier

import app.corkboard.notifier.config.NotifierProperties
import app.corkboard.notifier.mail.ProviderRateLimiter
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RateLimitTest {

    private fun limiter(perSecond: Int, burst: Int = 0) =
        ProviderRateLimiter(
            NotifierProperties(
                apiKey = "test",
                rate = NotifierProperties.Rate(perSecond = perSecond, burst = burst),
            )
        )

    @Test
    fun `a free permit is handed over without any waiting`() {
        limiter(perSecond = 10, burst = 3).use { limiter ->
            repeat(3) { assertThat(limiter.tryAcquireNow()).isTrue() }
            assertThat(limiter.tryAcquireNow())
                .describedAs("a fourth message has to wait for a refill")
                .isFalse()

            assertThat(limiter.tryAcquire(Duration.ofMillis(500)))
                .describedAs("waiting a moment gets a token at 10/s")
                .isTrue()
        }
    }

    @Test
    fun `permits that are not due yet are promised, not waited for`() {
        limiter(perSecond = 20, burst = 1).use { limiter ->
            val started = System.nanoTime()
            val flights = (1..4).map { limiter.acquire() }
            val handedBack = (System.nanoTime() - started) / 1_000_000

            assertThat(handedBack)
                .describedAs("the caller is never parked — it gets futures back at once")
                .isLessThan(50)

            CompletableFuture.allOf(*flights.toTypedArray()).get(5, TimeUnit.SECONDS)
            val settled = (System.nanoTime() - started) / 1_000_000
            assertThat(settled)
                .describedAs("three refills at 20/s land about 150ms later")
                .isGreaterThanOrEqualTo(100)
        }
    }

    @Test
    fun `a non-positive rate turns the limiter off`() {
        limiter(perSecond = 0).use { limiter ->
            repeat(50) { assertThat(limiter.tryAcquireNow()).isTrue() }
            assertThat(limiter.acquire()).isCompleted
        }
    }
}
