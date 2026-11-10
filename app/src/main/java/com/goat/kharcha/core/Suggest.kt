package com.goat.kharcha.core

/** Picks likely categories for a payment the app hasn't seen before. */
object Suggest {

    private val KEYWORDS: List<Pair<String, Regex>> = listOf(
        "bills" to Regex("""(?i)iesco|lesco|k-?electric|mepco|pesco|gepco|fesco|hesco|sngpl|ssgc|ptcl|storm ?fiber|nayatel|wateen|zong|\bjazz\b(?!cash)|telenor|ufone|electric|\bgas\b|\bwater\b|internet|broadband|\bbill\b|utility|recharge|top ?up"""),
        "food" to Regex("""(?i)foodpanda|food|cafe|coffee|chai|pizza|burger|kfc|mcdonald|cheezious|savou?r|restaurant|bakery|biryani|hardee|subway|domino|broast|ginsoy|kababjees|\btea\b|shawarma|\bbbq\b|dhaba|sweets|kitchen|grill|eatery|tikka|karahi|bundu|optp|jhonny|johnny|gloria jean|tim hortons|starbucks|dunkin"""),
        "groceries" to Regex("""(?i)\bmart\b|super ?market|super ?store|grocer|imtiaz|al-?fatah|\bmetro\b|carrefour|hyperstar|chase ?up|naheed|utility store|krave|airlift|green valley|springs|\bsave mart\b|cash ?& ?carry"""),
        "transport" to Regex("""(?i)careem|uber|indrive|bykea|yango|\bpso\b|shell|total ?parco|\btotal\b|attock|\bhascol\b|\bgo petrol|fuel|petrol|filling|motorway|m-?tag|parking|toll|daewoo|faisal movers|\bpia\b|airblue|serene|fly ?jinnah"""),
        "health" to Regex("""(?i)pharma|servaid|watson|clinic|hospital|\blab\b|chughtai|shifa|medic|dental|doctor|\bdr\.? |health|fazal din|dvago|marham|oladoc"""),
        "fun" to Regex("""(?i)netflix|spotify|youtube|google play|play store|steam|playstation|\bpsn\b|xbox|cinepax|cinema|nueplex|apple\.com|icloud|disney|prime video|tapmad|chatgpt|openai|claude|anthropic|canva|deezer|anghami"""),
        "shopping" to Regex("""(?i)daraz|outfitters|khaadi|gul ?ahmed|sapphire|\bj\.|limelight|breakout|temu|aliexpress|amazon|ideas|bonanza|nishat|alkaram|\bmall\b|ndure|servis|bata|stylo|cougar|engine|charcoal|shein|ikea|interwood|habitt|hush puppies|levi"""),
        "work" to Regex("""(?i)jlcpcb|pcbway|filament|upwork|fiverr|adobe|autodesk|solidworks|github|figma|notion|hostinger|namecheap|godaddy|\baws\b|google cloud|digikey|mouser|lcsc|hobbyking|creality|bambu|onshape|microsoft 365|office 365|zoom"""),
    )

    fun keywordCategory(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return KEYWORDS.firstOrNull { it.second.containsMatchIn(text) }?.first
    }

    fun incomeCategory(text: String): String = when {
        Regex("""(?i)payoneer|upwork|fiverr|\bwise\b|paypal|freelanc|client|invoice""").containsMatchIn(text) -> "client"
        Regex("""(?i)salary|payroll|\bsal\b""").containsMatchIn(text) -> "salary"
        Regex("""(?i)refund|revers|cash ?back""").containsMatchIn(text) -> "refund"
        else -> "transfer_in"
    }

    /**
     * Ranked category ids for an expense, best first. Order of evidence:
     * the person's saved rule for this place, keywords in the name or app, what they usually pick
     * around this hour, then what they use most overall.
     */
    fun rank(
        merchant: String?,
        context: String,
        hour: Int,
        rules: Map<String, String>,
        history: List<Tx>,
        categories: List<Category>,
    ): List<String> {
        val valid = categories.filter { !it.income }.sortedBy { it.order }.map { it.id }
        if (valid.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        rules[merchantKey(merchant)]?.let { if (it in valid) out += it }
        keywordCategory(merchant)?.let { if (it in valid) out += it }
        keywordCategory(context)?.let { if (it in valid) out += it }

        val tagged = history.filter { it.flow == Flow.OUT && it.categoryId != null && it.categoryId in valid }
        if (tagged.isNotEmpty()) {
            val zone = java.time.ZoneId.systemDefault()
            val near = tagged.filter {
                val h = java.time.Instant.ofEpochMilli(it.time).atZone(zone).hour
                val d = kotlin.math.abs(h - hour)
                minOf(d, 24 - d) <= 2
            }
            near.groupingBy { it.categoryId!! }.eachCount().entries.sortedByDescending { it.value }.take(2).forEach { out += it.key }
            tagged.groupingBy { it.categoryId!! }.eachCount().entries.sortedByDescending { it.value }.forEach { out += it.key }
        }
        valid.forEach { out += it }
        return out.toList()
    }
}
