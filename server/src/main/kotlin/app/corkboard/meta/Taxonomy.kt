package app.corkboard.meta

enum class EventType(
    val color: String,
    val applyableDefault: Boolean,
) {
    LOST_FOUND("#D9822B", true),
    ACTIVITY("#4C9A2A", true),
    CLUB("#7D5BA6", true),
    HELP("#4A76C7", true),
    GIVEAWAY("#2A9D8F", true),
    HAPPENING("#C94C4C", false),
    NOTICE("#8A8A8A", false);

    val key: String = name.lowercase()
    val labelKey: String = "event-type.$key"
}

data class Limits(
    val displayNameMax: Int = 50,
    val passwordMin: Int = 8,
    val passwordMax: Int = 128,
    val titleMin: Int = 3,
    val titleMax: Int = 120,
    val bodyMax: Int = 4000,
    val tagsMax: Int = 5,
    val tagNameMin: Int = 2,
    val tagNameMax: Int = 40,
    val messageMax: Int = 2000,
    val reportDetailMax: Int = 500,
    val expiryDefaultDays: Int = 30,
    val expiryMaxDays: Int = 90,
    val viewportLimitDefault: Int = 60,
    val viewportLimitMax: Int = 100,
)
