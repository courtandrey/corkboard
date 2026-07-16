package app.corkboard.common

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class RateLimiter(private val permitsPerMinute: Int) {

    private val buckets = ConcurrentHashMap<String, Bucket>()

    fun tryConsume(key: String): Boolean =
        buckets.computeIfAbsent(key) {
            Bucket.builder()
                .addLimit(
                    Bandwidth.builder()
                        .capacity(permitsPerMinute.toLong())
                        .refillGreedy(permitsPerMinute.toLong(), Duration.ofMinutes(1))
                        .build()
                )
                .build()
        }.tryConsume(1)
}
