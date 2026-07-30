package app.corkboard.seed

import app.corkboard.applications.ApplicationService
import app.corkboard.auth.AuthService
import app.corkboard.auth.RegisterRequest
import app.corkboard.common.CorkboardProperties
import app.corkboard.events.CreateEventRequest
import app.corkboard.events.EventService
import app.corkboard.events.LatLng
import app.corkboard.events.VoteService
import app.corkboard.jobs.ExpirationSweep
import app.corkboard.jooq.tables.references.EVENTS
import app.corkboard.jooq.tables.references.TAGS
import app.corkboard.jooq.tables.references.USERS
import app.corkboard.messaging.ApplicationStatus
import app.corkboard.messaging.ConversationService
import app.corkboard.meta.EventType
import app.corkboard.moderation.HideService
import app.corkboard.moderation.ReportReason
import app.corkboard.moderation.ReportRequest
import app.corkboard.moderation.ReportService
import java.time.Instant
import java.time.OffsetDateTime
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
    private val votes: VoteService,
    private val hides: HideService,
    private val reports: ReportService,
    private val applications: ApplicationService,
    private val conversations: ConversationService,
    private val sweep: ExpirationSweep,
    private val props: CorkboardProperties,
    private val context: ConfigurableApplicationContext,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)
    private val random = Random(20070214)

    private data class Created(
        val id: UUID,
        val authorId: UUID,
        val title: String,
        val applyable: Boolean,
        val expired: Boolean,
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
        if (dsl.fetchExists(USERS, USERS.EMAIL.eq(SeedData.DEMO_EMAIL))) {
            if (!props.seedForce) {
                log.info("Seed skipped: {} already exists (set SEED_FORCE=true to wipe and reload)", SeedData.DEMO_EMAIL)
                return
            }
            wipe()
        }

        val demo = register(SeedData.DEMO_EMAIL, "Demo Resident", props.seedDemoPassword)
        val residents = SeedData.RESIDENTS.map { name ->
            register(
                "${name.lowercase().replace(Regex("[^a-z]+"), ".").trim('.')}@corkboard.local",
                name,
                "Seed-${UUID.randomUUID()}",
            )
        }
        val users = residents + demo

        val created = mutableListOf<Created>()

        SeedData.HANDCRAFTED.forEachIndexed { i, note ->
            val authorId = when {
                note.title == SeedData.PIROZHOK_TITLE -> demo
                i % 7 == 3 -> demo
                else -> residents[i % residents.size]
            }
            created += create(
                authorId,
                note.type,
                note.title,
                note.body,
                jitter(note.near, 0.012, 0.008),
                note.applyable,
                Instant.now().plus(note.expiryDays, ChronoUnit.DAYS),
                note.tags,
            )
        }

        repeat(PROCEDURAL_COUNT) {
            val hood = pickNeighborhood()
            val type = pickType()
            val item = SeedData.FILLERS.getValue(type).random(random)
            val title = fill(SeedData.TITLE_TEMPLATES.getValue(type).random(random), item, hood.name)
            val body = fill(SeedData.BODY_TEMPLATES.random(random), SeedData.BODY_SNIPPETS.random(random), hood.name)
            val expired = random.nextDouble() < 0.03
            val expiresAt = if (expired) {
                Instant.now().minus(random.nextLong(1, 5), ChronoUnit.DAYS)
            } else {
                Instant.now().plus(random.nextLong(3, 61), ChronoUnit.DAYS)
            }
            val applyable = if (random.nextDouble() < 0.15) !type.applyableDefault else type.applyableDefault
            val tags = if (random.nextDouble() < 0.4) {
                List(random.nextInt(1, 3)) { SeedData.TAG_POOL.random(random) }.distinct()
            } else emptyList()
            created += create(
                residents.random(random), type, title, body,
                jitter(hood.lng to hood.lat, 0.016, 0.011),
                applyable, expiresAt, tags,
            )
        }

        castVotes(created, users)
        resolveSome(created)
        storyArc(created, demo, residents)
        extraApplications(created, users)
        scatterHides(created, residents)
        reportSpam(created, residents)

        sweep.sweep()

        log.info(
            "Seeded {} users, {} events ({} handcrafted), {} tags",
            users.size, created.size, SeedData.HANDCRAFTED.size, dsl.fetchCount(TAGS),
        )
    }

    private fun wipe() {
        log.info("SEED_FORCE set — wiping existing data")
        dsl.truncate(USERS).cascade().execute()
        dsl.truncate(TAGS).restartIdentity().cascade().execute()
    }

    private fun register(email: String, displayName: String, password: String): UUID {
        val id = auth.register(RegisterRequest(email, password, displayName), null).user.id
        dsl.update(USERS)
            .set(USERS.EMAIL_VERIFIED_AT, OffsetDateTime.now())
            .where(USERS.ID.eq(id))
            .execute()
        return id
    }

    private fun create(
        authorId: UUID,
        type: EventType,
        title: String,
        body: String,
        location: LatLng,
        applyable: Boolean,
        expiresAt: Instant,
        tags: List<String>,
    ): Created {
        val detail = events.create(
            authorId,
            CreateEventRequest(
                type = type,
                title = title.take(120),
                body = body,
                location = location,
                applyable = applyable,
                expiresAt = expiresAt,
                tags = tags,
            ),
        )
        return Created(detail.id, authorId, title, applyable, expired = expiresAt.isBefore(Instant.now()))
    }

    private fun castVotes(created: List<Created>, users: List<UUID>) {
        var total = 0
        for (event in created) {
            if (event.expired) continue
            val roll = random.nextDouble()
            val target = when {
                roll < 0.45 -> 0
                roll < 0.75 -> random.nextInt(1, 4)
                roll < 0.93 -> random.nextInt(4, 12)
                else -> random.nextInt(12, 23)
            }
            if (target == 0) continue
            val voters = users.filter { it != event.authorId }.shuffled(random).take(target)
            voters.forEach { votes.toggle(event.id, it) }
            total += voters.size
        }
        log.info("Cast {} votes", total)
    }

    private fun resolveSome(created: List<Created>) {
        val candidates = created.filter { !it.expired && it.title != SeedData.PIROZHOK_TITLE }
        val toResolve = candidates.shuffled(random).take((created.size * 0.08).toInt())
        toResolve.forEach { events.resolve(it.id, it.authorId) }
        log.info("Resolved {} events", toResolve.size)
    }

    private fun storyArc(created: List<Created>, demo: UUID, residents: List<UUID>) {
        val pirozhok = created.first { it.title == SeedData.PIROZHOK_TITLE }
        val (marisol, tommy, june) = residents.take(3)

        val sighting = applications.apply(
            pirozhok.id, marisol,
            "I think I saw him this morning by the community garden compost bins — grey tabby, red collar, very interested in someone's sandwich.",
        )
        applications.apply(
            pirozhok.id, tommy,
            "I put my number on the board at the laundromat and I'll keep an eye out on my night walks.",
        )
        applications.apply(
            pirozhok.id, june,
            "Checked the parking garage on 9th where the strays hang out — no luck yet, but I'll look again tomorrow.",
        )

        applications.updateStatus(sighting.application.id, demo, ApplicationStatus.ACCEPTED)
        val thread = sighting.conversationId
        conversations.send(thread, demo, "The compost bins! Of course. Was he still there when you left?")
        conversations.send(thread, marisol, "He was — I didn't want to spook him. I can stand watch by the gate if you head over now.")
        conversations.send(thread, demo, "On my way with the treat bag. Ten minutes.")
        conversations.send(thread, marisol, "He's here, he's fine, he's furious about the rain. See you at the gate.")
        conversations.send(thread, demo, "GOT HIM. He's home, eating like nothing happened. Thank you so, so much.")
        conversations.markRead(thread, demo)
        conversations.markRead(thread, marisol)

        events.resolve(pirozhok.id, demo)
        log.info("Story arc seeded (Pirozhok is home)")
    }

    private fun extraApplications(created: List<Created>, users: List<UUID>) {
        val applyable = created
            .filter { it.applyable && !it.expired && it.title != SeedData.PIROZHOK_TITLE }
            .shuffled(random)
            .take(6)
        for (event in applyable) {
            val applicants = users.filter { it != event.authorId }.shuffled(random).take(random.nextInt(1, 3))
            for (applicant in applicants) {
                runCatching {
                    applications.apply(
                        event.id, applicant,
                        listOf(
                            "Count me in — when works best?",
                            "I can help with this. Around most evenings.",
                            "Still available? I'm two streets over.",
                            "Sounds lovely, I'd like to join.",
                        ).random(random),
                    )
                }
            }
        }
    }

    private fun scatterHides(created: List<Created>, residents: List<UUID>) {
        val hiders = residents.shuffled(random).take(3)
        for (hider in hiders) {
            created.shuffled(random).take(2).forEach { event ->
                if (event.authorId != hider) runCatching { hides.hide(event.id, hider) }
            }
        }
    }

    private fun reportSpam(created: List<Created>, residents: List<UUID>) {
        val spam = created.first { it.title == SeedData.SPAM_TITLE }
        residents.filter { it != spam.authorId }.take(2).forEach { reporter ->
            reports.report(spam.id, reporter, ReportRequest(ReportReason.SPAM, "Obvious get-rich scheme."))
        }
        log.info("Reported the spammy note twice (below threshold)")
    }

    private fun pickNeighborhood(): Neighborhood {
        val total = SeedData.NEIGHBORHOODS.sumOf { it.weight }
        var roll = random.nextInt(total)
        for (hood in SeedData.NEIGHBORHOODS) {
            roll -= hood.weight
            if (roll < 0) return hood
        }
        return SeedData.NEIGHBORHOODS.last()
    }

    private fun pickType(): EventType {
        val weights = listOf(
            EventType.LOST_FOUND to 12, EventType.ACTIVITY to 14, EventType.CLUB to 12,
            EventType.HELP to 16, EventType.GIVEAWAY to 16, EventType.HAPPENING to 16,
            EventType.NOTICE to 14,
        )
        var roll = random.nextInt(weights.sumOf { it.second })
        for ((type, weight) in weights) {
            roll -= weight
            if (roll < 0) return type
        }
        return EventType.NOTICE
    }

    private fun fill(template: String, item: String, hood: String): String =
        template
            .replace("{item}", item)
            .replace("{snippet}", item)
            .replace("{place}", SeedData.LANDMARKS.random(random))
            .replace("{day}", SeedData.WEEKDAYS.random(random))
            .replace("{hood}", hood)

    private fun jitter(center: Pair<Double, Double>, lngSpread: Double, latSpread: Double): LatLng =
        LatLng(
            lng = center.first + (random.nextDouble() - 0.5) * lngSpread,
            lat = center.second + (random.nextDouble() - 0.5) * latSpread,
        )

    companion object {
        const val PROCEDURAL_COUNT = 250
    }
}
