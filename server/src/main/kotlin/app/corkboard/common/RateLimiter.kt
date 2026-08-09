package app.corkboard.common

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class RateLimiter(
    private val permitsPerMinute: Int,
    private val maxIdle: Duration = Duration.ofMinutes(2),
    private val pruneAt: Int = 10_000,
    private val ticker: () -> Long = System::nanoTime,
) {

    private class Entry(val bucket: Bucket) {
        @Volatile
        var lastUsed: Long = 0
    }

    private val buckets = ConcurrentHashMap<String, Entry>()

    fun tryConsume(key: String): Boolean {
        if (buckets.size > pruneAt) prune()
        val entry = buckets.computeIfAbsent(key) { Entry(newBucket()) }
        entry.lastUsed = ticker()
        return entry.bucket.tryConsume(1)
    }

    private fun prune() {
        val cutoff = ticker() - maxIdle.toNanos()
        buckets.values.removeIf { it.lastUsed < cutoff }
    }

    private fun newBucket(): Bucket =
        Bucket.builder()
            .addLimit(
                Bandwidth.builder()
                    .capacity(permitsPerMinute.toLong())
                    .refillGreedy(permitsPerMinute.toLong(), Duration.ofMinutes(1))
                    .build()
            )
            .build()
}
