package app.corkboard.features

import app.corkboard.common.CorkboardProperties
import java.sql.Connection
import javax.sql.DataSource
import org.postgresql.PGConnection
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component

@Component
class FeatureFlagBus(
    private val dataSource: DataSource,
    private val flags: FeatureFlagService,
    private val props: CorkboardProperties,
) : SmartLifecycle {

    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var running = false

    private var worker: Thread? = null

    override fun start() {
        if (!props.featureFlags.listen) {
            log.info("feature flag bus is off — this instance only sees its own changes")
            return
        }
        running = true
        worker = Thread.ofPlatform().daemon().name("feature-flag-bus").start(::listen)
    }

    override fun stop() {
        running = false
        worker?.join(STOP_TIMEOUT_MILLIS)
        worker = null
    }

    override fun isRunning(): Boolean = running

    private fun listen() {
        while (running) {
            try {
                dataSource.connection.use(::follow)
            } catch (e: Exception) {
                if (running) {
                    log.warn("feature flag bus dropped — reconnecting", e)
                    runCatching { Thread.sleep(RECONNECT_MILLIS) }.onFailure { return }
                }
            }
        }
    }

    private fun follow(connection: Connection) {
        connection.createStatement().use { it.execute("LISTEN $CHANNEL") }
        val pg = connection.unwrap(PGConnection::class.java)

        flags.refresh()
        while (running) {
            if (!pg.getNotifications(POLL_MILLIS).isNullOrEmpty()) flags.refresh()
        }
    }

    companion object {
        const val CHANNEL = "corkboard_feature_flags"

        private const val POLL_MILLIS = 1_000
        private const val RECONNECT_MILLIS = 2_000L
        private const val STOP_TIMEOUT_MILLIS = 3_000L
    }
}
