package app.corkboard.seed

import app.corkboard.auth.AuthService
import app.corkboard.auth.RegisterRequest
import app.corkboard.common.CorkboardProperties
import app.corkboard.events.CreateEventRequest
import app.corkboard.events.EventService
import app.corkboard.events.LatLng
import app.corkboard.jooq.tables.references.USERS
import app.corkboard.meta.EventType
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.random.Random
import kotlin.system.exitProcess
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("seed")
class SeedRunner(
    private val dsl: DSLContext,
    private val auth: AuthService,
    private val events: EventService,
    private val props: CorkboardProperties,
    private val context: ConfigurableApplicationContext,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)
    private val random = Random(20070214)

    private data class SeedNote(
        val type: EventType,
        val title: String,
        val body: String,
        val near: Pair<Double, Double>,
        val applyable: Boolean,
        val tags: List<String> = emptyList(),
        val expiryDays: Long = 30,
    )

    override fun run(args: ApplicationArguments) {
        val exitCode = try {
            seed()
            0
        } catch (e: Exception) {
            log.error("Seed failed", e)
            1
        }
        exitProcess(SpringApplication.exit(context, { exitCode }))
    }

    private fun seed() {
        check(props.seedDemoPassword.isNotBlank()) { "SEED_DEMO_PASSWORD must be set to seed" }
        if (dsl.fetchExists(USERS, USERS.EMAIL.eq(DEMO_EMAIL))) {
            log.info("Seed skipped: {} already exists", DEMO_EMAIL)
            return
        }

        val demo = auth.register(
            RegisterRequest(DEMO_EMAIL, props.seedDemoPassword, "Demo Resident"), null,
        ).user.id
        val residents = RESIDENTS.map { name ->
            auth.register(
                RegisterRequest(
                    "${name.lowercase().replace(' ', '.')}@corkboard.local",
                    "Seed-${UUID.randomUUID()}",
                    name,
                ),
                null,
            ).user.id
        }
        val authors = residents + demo

        NOTES.forEachIndexed { i, note ->
            val authorId = if (note.title.contains("Pirozhok")) demo else authors[i % authors.size]
            events.create(
                authorId,
                CreateEventRequest(
                    type = note.type,
                    title = note.title,
                    body = note.body,
                    location = jitter(note.near),
                    applyable = note.applyable,
                    expiresAt = Instant.now().plus(note.expiryDays, ChronoUnit.DAYS),
                    tags = note.tags,
                ),
            )
        }
        log.info("Seeded {} users and {} events", authors.size, NOTES.size)
    }

    private fun jitter(center: Pair<Double, Double>): LatLng =
        LatLng(
            lng = center.first + (random.nextDouble() - 0.5) * 0.012,
            lat = center.second + (random.nextDouble() - 0.5) * 0.008,
        )

    companion object {
        const val DEMO_EMAIL = "demo@corkboard.local"

        private val RESIDENTS = listOf(
            "Marisol Vega", "Tommy Okafor", "June Park", "Sasha Lindqvist", "Ray Delgado",
            "Priya Raman", "Old Gus", "Wendy Liu", "Bram de Vries", "Katya Morozova",
        )

        private val EAST_VILLAGE = -73.9816 to 40.7265
        private val WILLIAMSBURG = -73.9573 to 40.7081
        private val PARK_SLOPE = -73.9776 to 40.6710
        private val UWS = -73.9754 to 40.7870
        private val HARLEM = -73.9465 to 40.8116
        private val MIDTOWN = -73.9857 to 40.7484
        private val BUSHWICK = -73.9210 to 40.6944
        private val LES = -73.9871 to 40.7180
        private val ASTORIA = -73.9235 to 40.7644
        private val BLIJDORP = 4.4530 to 51.9310
        private val KRALINGEN = 4.5080 to 51.9260
        private val DELFSHAVEN = 4.4430 to 51.9040

        private val NOTES = listOf(
            SeedNote(
                EventType.LOST_FOUND, "Missing cat Pirozhok — grey tabby, red collar",
                "He slipped out Tuesday night near Tompkins Square. Shy but food-motivated; rattle a treat bag and he'll come to you. Please write if you spot him, his humans are worried sick.",
                EAST_VILLAGE, applyable = true, tags = listOf("cats", "east-village"), expiryDays = 21,
            ),
            SeedNote(
                EventType.LOST_FOUND, "Found: single house key on a frog keychain",
                "Picked it up by the dog run gate on Saturday morning. Describe the frog and it's yours.",
                EAST_VILLAGE, applyable = true, tags = listOf("found"),
            ),
            SeedNote(
                EventType.ACTIVITY, "Five-a-side football, Sunday mornings",
                "We're four regulars short since the weather turned. East River Park fields at 9am, all levels genuinely welcome — we play for the coffee afterwards as much as the game.",
                LES, applyable = true, tags = listOf("5-a-side", "beginners-welcome"), expiryDays = 45,
            ),
            SeedNote(
                EventType.ACTIVITY, "Morning run club — slow pace, fast gossip",
                "Loop around Prospect Park, 7am Tuesdays and Fridays. We wait for stragglers at the boathouse.",
                PARK_SLOPE, applyable = true, tags = listOf("running", "beginners-welcome"), expiryDays = 60,
            ),
            SeedNote(
                EventType.CLUB, "Chess in the park — bring your own clock",
                "Every Saturday by the fountain, weather permitting. Blitz until someone's phone dies. Kibitzers tolerated, barely.",
                UWS, applyable = true, tags = listOf("chess"), expiryDays = 60,
            ),
            SeedNote(
                EventType.CLUB, "Board games night above the laundromat",
                "Thursdays at 7. We own too many games and not enough friends. Catan ban currently in effect after The Incident.",
                BUSHWICK, applyable = true, tags = listOf("board-games", "beginners-welcome"), expiryDays = 60,
            ),
            SeedNote(
                EventType.HELP, "Need a hand moving a couch two blocks",
                "It's a two-seater, not a monster. Saturday around noon, pizza and eternal gratitude included.",
                WILLIAMSBURG, applyable = true, tags = listOf("moving"), expiryDays = 7,
            ),
            SeedNote(
                EventType.HELP, "Offering: bike repair on weekends",
                "Retired mechanic, miss the work. Flats, brakes, gears — bring it by the community garden Saturday mornings. Donations go to the garden's seed fund.",
                HARLEM, applyable = true, tags = listOf("bikes"), expiryDays = 60,
            ),
            SeedNote(
                EventType.HELP, "Dog walker needed, gentle old beagle",
                "Biscuit is eleven and walks like it. Twenty minutes at lunchtime, weekdays. He will love you unconditionally and immediately.",
                ASTORIA, applyable = true, tags = listOf("dogs"), expiryDays = 30,
            ),
            SeedNote(
                EventType.GIVEAWAY, "Free: bookshelf, solid pine, slightly scratched",
                "Moving out, can't take it. Fits a lot of books and one medium cat. First come, first served — stoop pickup on 7th street.",
                PARK_SLOPE, applyable = true, tags = listOf("furniture"), expiryDays = 10,
            ),
            SeedNote(
                EventType.GIVEAWAY, "Sourdough starter, needs a good home",
                "His name is Clint Yeastwood. Fed daily, very active. I'm traveling for two months and he deserves better.",
                WILLIAMSBURG, applyable = true, tags = listOf("baking"), expiryDays = 14,
            ),
            SeedNote(
                EventType.GIVEAWAY, "Moving box mountain — free for the taking",
                "About thirty sturdy boxes, flattened, plus packing paper. Take some, take all.",
                MIDTOWN, applyable = true, expiryDays = 7,
            ),
            SeedNote(
                EventType.HAPPENING, "Stoop sale marathon on Berry Street",
                "Six households, one Saturday, everything from vinyl to a kayak. Starts at 10, the good stuff goes by 11.",
                WILLIAMSBURG, applyable = false, tags = listOf("stoop-sale"), expiryDays = 5,
            ),
            SeedNote(
                EventType.HAPPENING, "Open-air movie night: The Princess Bride",
                "Bring a blanket to the community garden Friday at dusk. Popcorn provided by Gus, who insists it's the good kind.",
                HARLEM, applyable = false, tags = listOf("movies"), expiryDays = 8,
            ),
            SeedNote(
                EventType.HAPPENING, "Saturday farmers market is back",
                "The cider doughnut stand returned. This is not a drill. Under the elevated tracks, 8am to 2pm.",
                ASTORIA, applyable = false, expiryDays = 30,
            ),
            SeedNote(
                EventType.NOTICE, "Water shutoff Thursday morning, our block",
                "Con Ed says 9am to noon for the buildings between 4th and 6th. Fill a kettle the night before.",
                EAST_VILLAGE, applyable = false, expiryDays = 4,
            ),
            SeedNote(
                EventType.NOTICE, "Please stop feeding the pigeons on the corner",
                "They have unionized. They wait for the 8:15 lady like clockwork and the sidewalk shows it. The bench people beg you.",
                UWS, applyable = false, expiryDays = 30,
            ),
            SeedNote(
                EventType.NOTICE, "New bike lane painting next week",
                "Bedford between N 4th and Metropolitan, Monday through Wednesday. Expect cones, confusion, and one very proud city van.",
                WILLIAMSBURG, applyable = false, tags = listOf("bikes"), expiryDays = 12,
            ),
            SeedNote(
                EventType.LOST_FOUND, "Lost: kid's scooter, blue with dinosaur stickers",
                "Left outside the bakery for ten minutes. My son has been a stoic little soldier about it but I know that scooter meant the world.",
                PARK_SLOPE, applyable = true, tags = listOf("lost"), expiryDays = 20,
            ),
            SeedNote(
                EventType.ACTIVITY, "Beginner tai chi on the pier",
                "Wednesdays at sunrise. Wear comfortable shoes, expect herons.",
                BLIJDORP, applyable = true, tags = listOf("beginners-welcome"), expiryDays = 60,
            ),
            SeedNote(
                EventType.GIVEAWAY, "Gratis: stapel NL-studieboeken",
                "Inburgering gehaald! Boeken mogen door naar de volgende. Afhalen in Kralingen.",
                KRALINGEN, applyable = true, tags = listOf("books"), expiryDays = 21,
            ),
            SeedNote(
                EventType.CLUB, "Klaverjassen in het buurthuis",
                "Dinsdagavond, inzet is de eer en een rol koeken. Nieuwe leden van harte welkom.",
                DELFSHAVEN, applyable = true, tags = listOf("board-games"), expiryDays = 60,
            ),
            SeedNote(
                EventType.HAPPENING, "Canal-side vinyl swap",
                "Crates out along the water Sunday afternoon. Bring records, leave with different records. That's the whole event.",
                DELFSHAVEN, applyable = false, tags = listOf("music"), expiryDays = 9,
            ),
            SeedNote(
                EventType.NOTICE, "Brug dicht voor onderhoud dit weekend",
                "De fietsbrug bij het park is zaterdag en zondag afgesloten. Omleiding via de sluis.",
                BLIJDORP, applyable = false, expiryDays = 3,
            ),
            SeedNote(
                EventType.LOST_FOUND, "Gevonden: bos sleutels met bakfiets-hanger",
                "Lagen op het bankje bij de speeltuin. Herken je de hanger, stuur een berichtje.",
                KRALINGEN, applyable = true, tags = listOf("found"), expiryDays = 14,
            ),
        )
    }
}
