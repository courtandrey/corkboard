package app.corkboard.seed

import app.corkboard.auth.AuthService
import app.corkboard.auth.RegisterRequest
import app.corkboard.jooq.enums.EventStatus
import app.corkboard.jooq.enums.EventType
import app.corkboard.jooq.tables.references.EVENTS
import java.time.OffsetDateTime
import java.util.Random
import java.util.UUID
import kotlin.system.exitProcess
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Geometry
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
@Profile("perfseed")
class PerfSeedRunner(
    private val dsl: DSLContext,
    private val auth: AuthService,
    private val environment: Environment,
    private val context: ConfigurableApplicationContext,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)
    private val random = Random(42)

    private data class City(val name: String, val lng: Double, val lat: Double, val weight: Int, val spread: Double)

    override fun run(args: ApplicationArguments) {
        val exitCode = try {
            seed()
            0
        } catch (e: Exception) {
            log.error("Perf seed failed", e)
            1
        }
        exitProcess(SpringApplication.exit(context, { exitCode }))
    }

    private fun seed() {
        val total = environment.getProperty("PERF_EVENT_COUNT", "100000").toInt()
        val authorId = auth.register(
            RegisterRequest(
                "perf-${UUID.randomUUID()}@corkboard.local",
                "Perf-${UUID.randomUUID()}",
                "Perf Fixture",
                "perf_${UUID.randomUUID().toString().take(8).replace("-", "")}",
            ),
            null,
        ).user.id

        val clusterCount = (total * 0.85).toInt()
        val uniformCount = total - clusterCount
        val weightSum = CITIES.sumOf { it.weight }
        val now = OffsetDateTime.now()

        var inserted = 0
        val batch = mutableListOf<Array<Any?>>()

        fun flush() {
            if (batch.isEmpty()) return
            var insert = dsl.insertInto(
                EVENTS,
                EVENTS.AUTHOR_ID, EVENTS.TYPE, EVENTS.STATUS, EVENTS.TITLE, EVENTS.BODY,
                EVENTS.LOCATION, EVENTS.APPLYABLE, EVENTS.SCORE, EVENTS.EXPIRES_AT, EVENTS.RESOLVED_AT,
            )
            for (row in batch) {
                insert = insert.values(
                    listOf(
                        row[0], row[1], row[2], row[3], row[4],
                        point(row[5] as Double, row[6] as Double), row[7], row[8], row[9], row[10],
                    )
                )
            }
            insert.execute()
            inserted += batch.size
            if (inserted % 20_000 < batch.size) log.info("Inserted {} / {}", inserted, total)
            batch.clear()
        }

        fun add(lng: Double, lat: Double, label: String) {
            val statusRoll = random.nextDouble()
            val status = when {
                statusRoll < 0.08 -> EventStatus.resolved
                statusRoll < 0.13 -> EventStatus.expired
                else -> EventStatus.active
            }
            val score = (random.nextGaussian().let { g -> (g * g * 4).toInt() }).coerceIn(0, 40)
            val type = EventType.entries[random.nextInt(EventType.entries.size)]
            batch += arrayOf(
                authorId, type, status,
                "Perf note ${inserted + batch.size} ($label)",
                "Benchmark fixture body near $label.",
                lng, lat,
                random.nextDouble() < 0.5, score,
                now.plusDays((1 + random.nextInt(60)).toLong()),
                if (status == EventStatus.resolved) now.minusHours(random.nextInt(200).toLong()) else null,
            )
            if (batch.size >= 500) flush()
        }

        repeat(clusterCount) {
            var roll = random.nextInt(weightSum)
            val city = CITIES.first { c ->
                roll -= c.weight
                roll < 0
            }
            val lng = (city.lng + random.nextGaussian() * city.spread).coerceIn(-179.9, 179.9)
            val lat = (city.lat + random.nextGaussian() * city.spread * 0.7).coerceIn(-84.0, 84.0)
            add(lng, lat, city.name)
        }
        repeat(uniformCount) {
            add(random.nextDouble() * 360 - 180, random.nextDouble() * 160 - 80, "nowhere in particular")
        }
        flush()
        log.info("Perf seed complete: {} events", inserted)
    }

    private fun point(lng: Double, lat: Double): Field<Geometry?> {
        @Suppress("UNCHECKED_CAST")
        return DSL.field(
            "ST_SetSRID(ST_MakePoint({0}, {1}), 4326)", Geometry::class.java,
            DSL.`val`(lng), DSL.`val`(lat),
        ) as Field<Geometry?>
    }

    companion object {
        private val CITIES = listOf(
            City("New York", -73.98, 40.75, 60, 0.12),
            City("Tokyo", 139.69, 35.68, 60, 0.15),
            City("London", -0.12, 51.51, 45, 0.12),
            City("Paris", 2.35, 48.86, 35, 0.10),
            City("São Paulo", -46.63, -23.55, 45, 0.15),
            City("Mexico City", -99.13, 19.43, 35, 0.12),
            City("Cairo", 31.24, 30.05, 30, 0.10),
            City("Mumbai", 72.88, 19.08, 45, 0.10),
            City("Delhi", 77.21, 28.61, 40, 0.12),
            City("Shanghai", 121.47, 31.23, 45, 0.12),
            City("Beijing", 116.41, 39.90, 35, 0.12),
            City("Lagos", 3.38, 6.52, 30, 0.10),
            City("Istanbul", 28.98, 41.01, 30, 0.10),
            City("Moscow", 37.62, 55.76, 30, 0.12),
            City("Los Angeles", -118.24, 34.05, 35, 0.18),
            City("Chicago", -87.63, 41.88, 25, 0.12),
            City("Toronto", -79.38, 43.65, 20, 0.10),
            City("Buenos Aires", -58.38, -34.60, 25, 0.10),
            City("Rio de Janeiro", -43.17, -22.91, 20, 0.10),
            City("Berlin", 13.40, 52.52, 20, 0.10),
            City("Amsterdam", 4.90, 52.37, 15, 0.06),
            City("Rotterdam", 4.48, 51.92, 10, 0.05),
            City("Madrid", -3.70, 40.42, 20, 0.08),
            City("Rome", 12.50, 41.90, 18, 0.08),
            City("Warsaw", 21.01, 52.23, 12, 0.08),
            City("Stockholm", 18.07, 59.33, 10, 0.08),
            City("Oslo", 10.75, 59.91, 6, 0.06),
            City("Helsinki", 24.94, 60.17, 6, 0.06),
            City("Athens", 23.73, 37.98, 10, 0.08),
            City("Dubai", 55.27, 25.20, 15, 0.10),
            City("Singapore", 103.85, 1.29, 20, 0.05),
            City("Jakarta", 106.85, -6.21, 30, 0.12),
            City("Bangkok", 100.50, 13.76, 25, 0.10),
            City("Seoul", 126.98, 37.57, 35, 0.10),
            City("Sydney", 151.21, -33.87, 20, 0.12),
            City("Melbourne", 144.96, -37.81, 15, 0.10),
            City("Auckland", 174.76, -36.85, 6, 0.08),
            City("Cape Town", 18.42, -33.93, 12, 0.10),
            City("Nairobi", 36.82, -1.29, 12, 0.08),
            City("Lima", -77.04, -12.05, 15, 0.10),
            City("Bogotá", -74.07, 4.71, 15, 0.08),
            City("Vancouver", -123.12, 49.28, 10, 0.08),
            City("Anchorage", -149.90, 61.22, 2, 0.10),
            City("Reykjavik", -21.94, 64.15, 2, 0.05),
            City("Honolulu", -157.86, 21.31, 3, 0.05),
        )
    }
}
