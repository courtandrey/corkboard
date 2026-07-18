package app.corkboard.seed

import app.corkboard.meta.EventType

data class SeedNote(
    val type: EventType,
    val title: String,
    val body: String,
    val near: Pair<Double, Double>,
    val applyable: Boolean,
    val tags: List<String> = emptyList(),
    val expiryDays: Long = 30,
)

data class Neighborhood(
    val name: String,
    val lng: Double,
    val lat: Double,
    val weight: Int,
)

object SeedData {

    const val DEMO_EMAIL = "demo@corkboard.local"
    const val PIROZHOK_TITLE = "Missing cat Pirozhok — grey tabby, red collar"
    const val SPAM_TITLE = "MAKE \$\$\$ FROM HOME — ask me how"

    val RESIDENTS = listOf(
        "Marisol Vega", "Tommy Okafor", "June Park", "Sasha Lindqvist", "Ray Delgado",
        "Priya Raman", "Old Gus", "Wendy Liu", "Bram de Vries", "Katya Morozova",
        "Dot Kowalski", "Hector Alvarez", "Nina Osei", "Frank DiMaggio", "Yuki Tanaka",
        "Beatrix Hummel", "Omar Haddad", "Celia Nascimento", "Stan the Super", "Ada Nwosu",
        "Pieter Janssen", "Rosa Almeida", "Mikkel Sørensen", "Tess O'Rourke", "Lev Abramov",
    )

    val EAST_VILLAGE = -73.9816 to 40.7265
    val LES = -73.9871 to 40.7180
    val WEST_VILLAGE = -74.0027 to 40.7347
    val CHELSEA = -74.0014 to 40.7465
    val MIDTOWN = -73.9857 to 40.7484
    val UWS = -73.9754 to 40.7870
    val UES = -73.9565 to 40.7736
    val HARLEM = -73.9465 to 40.8116
    val WASHINGTON_HEIGHTS = -73.9396 to 40.8417
    val FIDI = -74.0090 to 40.7075
    val WILLIAMSBURG = -73.9573 to 40.7081
    val GREENPOINT = -73.9538 to 40.7245
    val PARK_SLOPE = -73.9776 to 40.6710
    val BUSHWICK = -73.9210 to 40.6944
    val BED_STUY = -73.9418 to 40.6872
    val CROWN_HEIGHTS = -73.9442 to 40.6694
    val SUNSET_PARK = -74.0048 to 40.6527
    val BRIGHTON_BEACH = -73.9614 to 40.5776
    val ASTORIA = -73.9235 to 40.7644
    val JACKSON_HEIGHTS = -73.8830 to 40.7557
    val FLUSHING = -73.8331 to 40.7674
    val RIDGEWOOD = -73.9060 to 40.7043
    val MOTT_HAVEN = -73.9230 to 40.8091
    val FORDHAM = -73.8987 to 40.8592
    val ST_GEORGE = -74.0776 to 40.6437
    val BLIJDORP = 4.4530 to 51.9310
    val KRALINGEN = 4.5080 to 51.9260
    val DELFSHAVEN = 4.4430 to 51.9040

    val NEIGHBORHOODS = listOf(
        Neighborhood("East Village", EAST_VILLAGE.first, EAST_VILLAGE.second, 3),
        Neighborhood("Lower East Side", LES.first, LES.second, 2),
        Neighborhood("West Village", WEST_VILLAGE.first, WEST_VILLAGE.second, 2),
        Neighborhood("Chelsea", CHELSEA.first, CHELSEA.second, 2),
        Neighborhood("Midtown", MIDTOWN.first, MIDTOWN.second, 2),
        Neighborhood("Upper West Side", UWS.first, UWS.second, 2),
        Neighborhood("Upper East Side", UES.first, UES.second, 2),
        Neighborhood("Harlem", HARLEM.first, HARLEM.second, 2),
        Neighborhood("Washington Heights", WASHINGTON_HEIGHTS.first, WASHINGTON_HEIGHTS.second, 1),
        Neighborhood("Financial District", FIDI.first, FIDI.second, 1),
        Neighborhood("Williamsburg", WILLIAMSBURG.first, WILLIAMSBURG.second, 3),
        Neighborhood("Greenpoint", GREENPOINT.first, GREENPOINT.second, 2),
        Neighborhood("Park Slope", PARK_SLOPE.first, PARK_SLOPE.second, 2),
        Neighborhood("Bushwick", BUSHWICK.first, BUSHWICK.second, 2),
        Neighborhood("Bed-Stuy", BED_STUY.first, BED_STUY.second, 2),
        Neighborhood("Crown Heights", CROWN_HEIGHTS.first, CROWN_HEIGHTS.second, 1),
        Neighborhood("Sunset Park", SUNSET_PARK.first, SUNSET_PARK.second, 1),
        Neighborhood("Brighton Beach", BRIGHTON_BEACH.first, BRIGHTON_BEACH.second, 1),
        Neighborhood("Astoria", ASTORIA.first, ASTORIA.second, 2),
        Neighborhood("Jackson Heights", JACKSON_HEIGHTS.first, JACKSON_HEIGHTS.second, 1),
        Neighborhood("Flushing", FLUSHING.first, FLUSHING.second, 1),
        Neighborhood("Ridgewood", RIDGEWOOD.first, RIDGEWOOD.second, 1),
        Neighborhood("Mott Haven", MOTT_HAVEN.first, MOTT_HAVEN.second, 1),
        Neighborhood("Fordham", FORDHAM.first, FORDHAM.second, 1),
        Neighborhood("St. George", ST_GEORGE.first, ST_GEORGE.second, 1),
    )

    val TAG_POOL = listOf(
        "beginners-welcome", "free", "kids", "dogs", "cats", "bikes", "books", "music",
        "board-games", "running", "chess", "gardening", "food", "spanish-speaking",
        "russian-speaking", "weekend", "evenings", "seniors", "volunteering", "swap",
    )

    val WEEKDAYS = listOf("Saturday", "Sunday", "Tuesday", "Thursday", "Friday")

    val LANDMARKS = listOf(
        "the fountain", "the dog run", "the church steps", "the corner bodega",
        "the community garden", "the old depot", "the playground", "the bakery",
        "the subway entrance", "the basketball courts",
    )

    val TITLE_TEMPLATES: Map<EventType, List<String>> = mapOf(
        EventType.LOST_FOUND to listOf(
            "Lost: {item} near {place}",
            "Found: {item} by {place}",
            "Has anyone seen a {item}?",
            "Found a {item} — describe it and it's yours",
        ),
        EventType.ACTIVITY to listOf(
            "{item} in {hood} — new faces welcome",
            "Weekly {item}, all levels",
            "Looking for people to join {item}",
            "{item} by {place}, weather permitting",
        ),
        EventType.CLUB to listOf(
            "{item} club meets {day}s",
            "Starting a {item} circle in {hood}",
            "{item} night — bring snacks",
            "Neighborhood {item} group",
        ),
        EventType.HELP to listOf(
            "Need a hand with {item}",
            "Offering: {item} for neighbors",
            "Anyone able to help with {item} this {day}?",
            "Help wanted: {item}, small thanks included",
        ),
        EventType.GIVEAWAY to listOf(
            "Free: {item}, come and get it",
            "Giving away {item} — first come first served",
            "{item} free to a good home",
            "Curb alert: {item} near {place}",
        ),
        EventType.HAPPENING to listOf(
            "{item} this {day} in {hood}",
            "Don't miss: {item} by {place}",
            "{item} — everyone's invited",
            "{item} on {day}, tell your neighbors",
        ),
        EventType.NOTICE to listOf(
            "Heads up: {item} on our block",
            "Notice: {item} starting {day}",
            "{item} — please plan around it",
            "PSA for {hood}: {item}",
        ),
    )

    val FILLERS: Map<EventType, List<String>> = mapOf(
        EventType.LOST_FOUND to listOf(
            "set of keys", "orange cat", "blue umbrella", "kid's mitten", "prescription glasses",
            "skateboard", "gray parrot", "wallet", "phone in a green case", "small terrier",
        ),
        EventType.ACTIVITY to listOf(
            "morning yoga", "pickup basketball", "casual tennis", "slow jogging", "tai chi",
            "swimming laps", "badminton", "sunrise walks", "table tennis", "bouldering trips",
        ),
        EventType.CLUB to listOf(
            "book", "chess", "knitting", "film", "cooking", "photography", "language exchange",
            "vinyl listening", "sketching", "bread-baking",
        ),
        EventType.HELP to listOf(
            "moving boxes", "assembling furniture", "walking my dog", "grocery runs",
            "fixing a leaky tap", "watering plants while away", "carrying groceries upstairs",
            "shoveling the stoop", "a school pickup", "hanging shelves",
        ),
        EventType.GIVEAWAY to listOf(
            "a couch", "moving boxes", "baby clothes", "a desk lamp", "kitchen chairs",
            "paperbacks", "a yoga mat", "plant cuttings", "a toaster", "picture frames",
        ),
        EventType.HAPPENING to listOf(
            "stoop sale", "open-air concert", "farmers market", "street cleanup",
            "community potluck", "outdoor movie night", "craft fair", "garden open day",
        ),
        EventType.NOTICE to listOf(
            "water shutoff", "street repaving", "scaffolding going up", "new bike lane",
            "tree pruning", "power maintenance", "school construction", "parking changes",
        ),
    )

    val BODY_TEMPLATES = listOf(
        "Neighbors of {hood} — {snippet}. Write a note if you can help or want in.",
        "Posting for our corner of {hood}: {snippet}. No strings attached, just neighborliness.",
        "This is a {hood} thing, {snippet}. Everyone from the block is welcome.",
        "Quick note for {hood}: {snippet}. Details over messages.",
        "Meet by {place} — {snippet}. Ask for details in a message.",
    )

    val BODY_SNIPPETS = listOf(
        "it happens more or less every week and nobody takes it too seriously",
        "bring nothing but yourself, maybe a thermos",
        "it started as a joke and now it has regulars",
        "the usual crowd is friendly and the new crowd becomes usual fast",
        "rain moves it to the covered part by the bakery",
        "first-timers get the good chair",
        "we've been doing this since the winter and it stuck",
        "kids and dogs tolerated enthusiastically",
    )

    val HANDCRAFTED = listOf(
        SeedNote(
            EventType.LOST_FOUND, PIROZHOK_TITLE,
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
            EventType.CLUB, "Russian-speaking book circle",
            "Раз в месяц обсуждаем одну книгу. Сейчас читаем Водолазкина. Новички и чай приветствуются.",
            BRIGHTON_BEACH, applyable = true, tags = listOf("books", "russian-speaking"), expiryDays = 60,
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
            EventType.LOST_FOUND, "Found: film camera on a park bench",
            "There's half a roll still in it. Tell me the make and it's back with you — I'd love to know how the photos turn out.",
            WEST_VILLAGE, applyable = true, tags = listOf("found"), expiryDays = 30,
        ),
        SeedNote(
            EventType.ACTIVITY, "Beginner salsa in the park pavilion",
            "Wednesdays at 7, music from a speaker with opinions. Nobody is good, that's the point. Partners rotate.",
            JACKSON_HEIGHTS, applyable = true, tags = listOf("dancing", "beginners-welcome"), expiryDays = 60,
        ),
        SeedNote(
            EventType.ACTIVITY, "Cold water swimming crew, Saturdays",
            "We meet at the pier at 8, shriek collectively, and get coffee after. Wetsuits optional, bragging mandatory.",
            BRIGHTON_BEACH, applyable = true, tags = listOf("swimming"), expiryDays = 45,
        ),
        SeedNote(
            EventType.CLUB, "Community garden seedling swap",
            "Trade tomato starts for herbs, cuttings for advice. Sunday afternoons by the toolshed until it gets cold.",
            BED_STUY, applyable = true, tags = listOf("gardening", "swap"), expiryDays = 40,
        ),
        SeedNote(
            EventType.CLUB, "Spanish–English conversation table",
            "Half an hour in each language, strict but friendly. La mesa del fondo del café, martes a las seis.",
            SUNSET_PARK, applyable = true, tags = listOf("spanish-speaking", "language"), expiryDays = 60,
        ),
        SeedNote(
            EventType.HELP, "Can someone teach me to parallel park?",
            "I have a license, a borrowed sedan, and a fear. Sunday mornings, empty street by the depot. I'll bring breakfast.",
            RIDGEWOOD, applyable = true, expiryDays = 21,
        ),
        SeedNote(
            EventType.HELP, "Offering: homework help, math and physics",
            "Retired teacher, two afternoons a week at the library. Middle and high school. Patience included.",
            FORDHAM, applyable = true, tags = listOf("kids", "volunteering"), expiryDays = 60,
        ),
        SeedNote(
            EventType.GIVEAWAY, "Piano. Free. You move it.",
            "Upright, mostly in tune, one sticky key that adds character. Ground floor, thank goodness. Serious inquiries only, strong friends recommended.",
            GREENPOINT, applyable = true, tags = listOf("music", "furniture"), expiryDays = 15,
        ),
        SeedNote(
            EventType.HAPPENING, "Ferry-watching picnic on the esplanade",
            "Not a boat club, just people who like boats. First Sunday of the month, bring something to share.",
            ST_GEORGE, applyable = false, expiryDays = 25,
        ),
        SeedNote(
            EventType.NOTICE, "Scaffolding going up on the avenue",
            "Six months, they say. The pigeons have already moved in. Mind your head at the corner entrance.",
            WASHINGTON_HEIGHTS, applyable = false, expiryDays = 30,
        ),
        SeedNote(
            EventType.NOTICE, SPAM_TITLE,
            "Unbelievable opportunity!!! Work from your kitchen!!! Message me for the secret. Not a pyramid, it's a triangle of success.",
            MIDTOWN, applyable = false, expiryDays = 30,
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
            DELFSHAVEN, applyable = false, tags = listOf("music", "swap"), expiryDays = 9,
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
        SeedNote(
            EventType.LOST_FOUND, "Vermist: cyperse kater Beer",
            "Sinds donderdag weg uit de Zwaerdecroonstraat. Groot, verlegen, gek op kip. Kijk even in uw schuur of kelder?",
            DELFSHAVEN, applyable = true, tags = listOf("cats"), expiryDays = 21,
        ),
        SeedNote(
            EventType.ACTIVITY, "Hardloopgroepje langs de Rotte",
            "Zondagochtend 9 uur, rustig tempo, koffie na. Regen is geen excuus, zegt Pieter.",
            BLIJDORP, applyable = true, tags = listOf("running", "beginners-welcome"), expiryDays = 60,
        ),
        SeedNote(
            EventType.HELP, "Wie kan een kastje ophangen?",
            "Twee planken, tien schroeven, nul talent hier. Gereedschap aanwezig, stroopwafels ook.",
            KRALINGEN, applyable = true, expiryDays = 14,
        ),
        SeedNote(
            EventType.HELP, "Aangeboden: boodschappen voor ouderen",
            "Ik loop dinsdag en vrijdag toch naar de markt. Lijstje in de bus of een berichtje is genoeg.",
            DELFSHAVEN, applyable = true, tags = listOf("seniors", "volunteering"), expiryDays = 60,
        ),
        SeedNote(
            EventType.GIVEAWAY, "Gratis balkonplanten, verhuizing",
            "Geraniums, een vetplant met ambitie en twee bakken lavendel. Weg is weg.",
            BLIJDORP, applyable = true, tags = listOf("gardening"), expiryDays = 8,
        ),
        SeedNote(
            EventType.HAPPENING, "Buurtborrel op het plein",
            "Vrijdag vanaf vijf. Iedereen neemt wat mee, niemand houdt een toespraak. Dat is de afspraak.",
            KRALINGEN, applyable = false, expiryDays = 6,
        ),
        SeedNote(
            EventType.CLUB, "Schaakavond in de bibliotheek",
            "Woensdag 19:30. Alle niveaus, borden aanwezig. De koffieautomaat doet het weer.",
            BLIJDORP, applyable = true, tags = listOf("chess"), expiryDays = 60,
        ),
        SeedNote(
            EventType.NOTICE, "Marktkramen verplaatst wegens werkzaamheden",
            "De dinsdagmarkt staat twee weken aan de andere kant van het kanaal. De viskraam blijft waar hij is, natuurlijk.",
            DELFSHAVEN, applyable = false, expiryDays = 14,
        ),
    )
}
