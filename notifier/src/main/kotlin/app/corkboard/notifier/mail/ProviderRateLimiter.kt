package app.corkboard.notifier.mail

import app.corkboard.notifier.config.NotifierProperties
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ProviderRateLimiter(private val props: NotifierProperties) : AutoCloseable {

    private val log = LoggerFactory.getLogger(javaClass)

    private val bucket: Bucket? =
        if (props.rate.limited) {
            Bucket.builder()
                .addLimit(
                    Bandwidth.builder()
                        .capacity(props.rate.capacity.toLong())
                        .refillGreedy(props.rate.perSecond.toLong(), Duration.ofSeconds(1))
                        .build()
                )
                .build()
        } else {
            log.warn("provider rate limiting is off (notifier.rate.per-second={})", props.rate.perSecond)
            null
        }

    private val clock: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread.ofPlatform().daemon().name("rate-limiter").unstarted(runnable)
        }

    fun tryAcquireNow(): Boolean = bucket?.tryConsume(1) ?: true

    fun acquire(): CompletableFuture<Void> {
        val bucket = bucket ?: return CompletableFuture.completedFuture(null)
        if (bucket.tryConsume(1)) return CompletableFuture.completedFuture(null)
        return bucket.asScheduler().consume(1, clock)
    }

    fun tryAcquire(maxWait: Duration): Boolean {
        val bucket = bucket ?: return true
        if (bucket.tryConsume(1)) return true
        if (maxWait.isZero || maxWait.isNegative) return false
        return bucket.asBlocking().tryConsume(1, maxWait)
    }

    fun availableTokens(): Long = bucket?.availableTokens ?: Long.MAX_VALUE

    override fun close() {
        clock.shutdownNow()
    }
}
