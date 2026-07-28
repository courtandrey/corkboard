package app.corkboard.notifier.mail

import app.corkboard.notifier.api.ApiException
import app.corkboard.notifier.api.ProblemCode
import app.corkboard.notifier.config.NotifierProperties
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component

@Component
class EmailDispatcher(
    private val emails: EmailService,
    private val limiter: ProviderRateLimiter,
    private val props: NotifierProperties,
) : AutoCloseable {

    private val log = LoggerFactory.getLogger(javaClass)

    private val workers: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()
    private val slots = Semaphore(props.sendConcurrency)

    val transport: String
        get() = emails.transport

    fun dispatch(request: EmailRequest): CompletableFuture<String> {
        emails.validate(request)
        return limiter.acquire().thenApplyAsync({ withSlot { emails.send(request) } }, workers)
    }

    fun dispatchNow(request: EmailRequest): String {
        emails.validate(request)
        val patience = Duration.ofMillis(props.rate.httpMaxWaitMillis)
        if (!limiter.tryAcquire(patience)) {
            throw ApiException(HttpStatus.TOO_MANY_REQUESTS, ProblemCode.RATE_LIMITED)
        }
        if (!slots.tryAcquire(patience.toMillis(), TimeUnit.MILLISECONDS)) {
            log.warn("all {} send slots are busy", props.sendConcurrency)
            throw ApiException(HttpStatus.TOO_MANY_REQUESTS, ProblemCode.RATE_LIMITED)
        }
        return try {
            emails.send(request)
        } finally {
            slots.release()
        }
    }

    private fun <T> withSlot(work: () -> T): T {
        slots.acquire()
        return try {
            work()
        } finally {
            slots.release()
        }
    }

    override fun close() {
        workers.shutdown()
        if (!workers.awaitTermination(20, TimeUnit.SECONDS)) workers.shutdownNow()
    }
}
