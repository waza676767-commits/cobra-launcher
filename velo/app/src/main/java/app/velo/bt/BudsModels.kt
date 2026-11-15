package app.velo.bt

enum class BudsStyle { BEANS, STEMS, OVER_EAR }

data class BudsModel(
    val family: String,
    val style: BudsStyle,
    val touch: Boolean,          // touch/press controls exist
    val maxTaps: Int,            // 1..3 tap gestures supported
    val hold: Boolean,
    val anc: Boolean,
    val makerApp: String?,       // package of the maker's companion app
    val makerName: String?
)

/** Recognises the earbuds from their Bluetooth name to pick the picture, gestures and features. */
object BudsModels {
    private val XIAOMI = "com.mi.earphone" to "Xiaomi Earbuds"

    private data class Rule(val re: Regex, val model: BudsModel)

    private val rules = listOf(
        Rule(Regex("redmi buds ?[5-9].*pro|xiaomi buds ?[4-9]", RegexOption.IGNORE_CASE),
            BudsModel("Xiaomi", BudsStyle.BEANS, true, 3, true, true, XIAOMI.first, XIAOMI.second)),
        Rule(Regex("redmi buds ?[6-9]", RegexOption.IGNORE_CASE),
            BudsModel("Xiaomi", BudsStyle.BEANS, true, 3, true, true, XIAOMI.first, XIAOMI.second)),
        Rule(Regex("redmi buds|redmi airdots|mi true wireless", RegexOption.IGNORE_CASE),
            BudsModel("Xiaomi", BudsStyle.BEANS, true, 3, true, false, XIAOMI.first, XIAOMI.second)),
        Rule(Regex("airpods max", RegexOption.IGNORE_CASE),
            BudsModel("Apple", BudsStyle.OVER_EAR, true, 3, true, true, null, null)),
        Rule(Regex("airpods", RegexOption.IGNORE_CASE),
            BudsModel("Apple", BudsStyle.STEMS, true, 3, true, true, null, null)),
        Rule(Regex("galaxy buds", RegexOption.IGNORE_CASE),
            BudsModel("Samsung", BudsStyle.BEANS, true, 3, true, true, "com.samsung.accessory.neobeanmanager", "Galaxy Buds")),
        Rule(Regex("wh-|wh1000", RegexOption.IGNORE_CASE),
            BudsModel("Sony", BudsStyle.OVER_EAR, true, 2, true, true, "com.sony.songpal.mdr", "Sony Headphones")),
        Rule(Regex("wf-|linkbuds", RegexOption.IGNORE_CASE),
            BudsModel("Sony", BudsStyle.BEANS, true, 3, true, true, "com.sony.songpal.mdr", "Sony Headphones")),
        Rule(Regex("soundcore|liberty|life ", RegexOption.IGNORE_CASE),
            BudsModel("Anker", BudsStyle.BEANS, true, 2, true, true, "com.oceanwing.soundcore", "Soundcore")),
        Rule(Regex("nothing|ear \\(|cmf buds", RegexOption.IGNORE_CASE),
            BudsModel("Nothing", BudsStyle.STEMS, true, 3, true, true, "com.nothing.smartcenter", "Nothing X")),
        Rule(Regex("pixel buds", RegexOption.IGNORE_CASE),
            BudsModel("Google", BudsStyle.BEANS, true, 2, true, true, "com.google.android.apps.wearables.maestro.companion", "Pixel Buds")),
        Rule(Regex("freebuds|honor earbuds", RegexOption.IGNORE_CASE),
            BudsModel("Huawei", BudsStyle.STEMS, true, 2, true, true, "com.huawei.audioaccessorymanager", "AI Life")),
        Rule(Regex("oneplus buds|nord buds", RegexOption.IGNORE_CASE),
            BudsModel("OnePlus", BudsStyle.STEMS, true, 3, true, true, "com.oneplus.twspods", "HeyMelody")),
        Rule(Regex("headphone|bose qc|quietcomfort|tune 7|tune 5|studio|beats solo|momentum", RegexOption.IGNORE_CASE),
            BudsModel("Generic", BudsStyle.OVER_EAR, true, 2, true, false, null, null))
    )

    private val generic = BudsModel("Generic", BudsStyle.BEANS, true, 3, true, false, null, null)

    fun match(name: String): BudsModel = rules.firstOrNull { it.re.containsMatchIn(name) }?.model ?: generic
}
