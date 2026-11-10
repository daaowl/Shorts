package com.goat.kharcha.core

/** What kind of app posted a notification. Decides how strict the parser is. */
enum class AppKind { FINANCE, SMS, MERCHANT, SOCIAL, OTHER }

data class Parsed(
    val amount: Long,
    val flow: Flow,
    val merchant: String?,
    val source: String,
)

/**
 * Reads payment notifications from any app: bank and wallet apps, SMS apps carrying bank texts,
 * and shops, delivery and ride apps. Pure Kotlin so it can be tested off-device.
 */
object Parser {

    private val SOCIAL_PKGS = listOf(
        "com.whatsapp", "org.telegram", "com.facebook", "com.instagram", "org.thoughtcrime.securesms",
        "com.snapchat", "com.discord", "com.slack", "com.microsoft.teams", "com.google.android.gm",
        "com.twitter", "com.x.android", "com.linkedin", "com.zhiliaoapp", "com.ss.android", "com.imo",
        "com.viber", "com.google.android.youtube", "com.reddit", "jp.naver.line", "com.skype",
        "com.google.android.apps.dynamite", "com.microsoft.office.outlook", "com.google.android.apps.photos",
        "com.google.android.calendar", "com.android.chrome", "com.google.android.googlequicksearchbox",
    )
    private val SOCIAL_WORDS = listOf("whatsapp", "telegram", "messenger", "instagram", "signal", "snapchat", "discord", "gmail", "outlook")

    private val SMS_PKGS = setOf(
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
        "com.android.messaging", "com.oneplus.mms", "com.truecaller", "com.textra", "com.miui.smsextra",
        "com.hihonor.mms", "com.huawei.message", "com.coloros.mms", "com.transsion.message", "com.motorola.messaging",
    )

    private val FINANCE_HINTS = listOf(
        "bank", "hbl", "meezan", "ubl", "mcb", "alfalah", "alfa", "jazzcash", "mobilink", "easypaisa",
        "telenor.phoenix", "sadapay", "nayapay", "payoneer", "paypal", "transferwise", "wise", "faysal", "askari",
        "allied", "bankislami", "jsbank", "zindigi", "konnect", "upaisa", "habibmetro", "standardchartered",
        "silkbank", "dubaiislamic", "raast", "wallet",
    )

    private val MERCHANT_WORDS = listOf(
        "foodpanda", "careem", "uber", "indrive", "bykea", "yango", "daraz", "cheetay", "airlift", "krave",
        "temu", "aliexpress", "amazon", "netflix", "spotify", "google play", "play store", "steam", "ptcl",
        "zong", "jazz", "telenor", "ufone", "khaadi", "outfitters", "kfc", "mcdonald", "domino", "savyour",
    )

    fun kind(pkg: String, label: String): AppKind {
        val p = pkg.lowercase()
        val l = label.lowercase()
        if (SOCIAL_PKGS.any { p == it || p.startsWith("$it.") } || SOCIAL_WORDS.any { l.contains(it) }) return AppKind.SOCIAL
        if (p in SMS_PKGS || l == "messages" || l == "messaging" || l == "message" || l.contains("sms")) return AppKind.SMS
        if (FINANCE_HINTS.any { p.contains(it) || l.contains(it) } || Regex("""\bpay\b|pay$|^pay""").containsMatchIn(l)) return AppKind.FINANCE
        if (MERCHANT_WORDS.any { l.contains(it) || p.contains(it.replace(" ", "")) }) return AppKind.MERCHANT
        return AppKind.OTHER
    }

    // ---------- patterns ----------

    private const val NUM = """((?:\d{1,3}(?:,\d{2,3})+|\d+)(?:\.\d{1,2})?)"""
    private val AMOUNT_PREFIX = Regex("""(?i)(?:\bpkr|\brs|₨)\s*\.?\s*:?\s*$NUM""")
    private val AMOUNT_SUFFIX = Regex("""(?i)$NUM\s*(?:pkr|rs|rupees?)\b""")

    private val HARD_REJECT = Regex(
        """\botp\b|one[- ]time (?:password|pin|code)|verification code|\bpasscode\b|\bcvv\b|\bpin\b|""" +
            """\bfailed\b|\bunsuccessful\b|\bdeclined\b|\binsufficient\b|could ?n[o']t|\bnot processed\b|""" +
            """\breminder\b|\bis due\b|\bdue date\b|\bdue on\b|\bamount due\b|\bpayable\b|\bdue by\b|""" +
            """will be (?:debited|charged|deducted|auto)|\brequested\b|\brequest(?:ing|s)? (?:rs|pkr|money|payment|for)\b|""" +
            """has been generated|\bbill generated\b|\be-?statement\b|\bbill of rs\b.*\bissued\b"""
    )

    private val PROMO = Regex(
        """\boffer\b|\bdiscount\b|\d+ ?% ?off\b|\bup ?to\b|\bwin\b|\bwinner\b|\bget rs\b|\bget pkr\b|\bsave rs\b|""" +
            """\bdeal\b|\bsale\b|\bcoupon\b|\bpromo\b|\bvoucher\b|\bhurry\b|\blimited\b|\bexclusive\b|\bavail\b|\benjoy\b|\bchance\b|\bflat rs\b"""
    )

    private val STRONG = Regex(
        """\bdebited\b|\bcredited\b|\bspent\b|\bpaid\b|\bsent\b|\breceived\b|\btransferred\b|\bcharged\b|""" +
            """\bpurchased?\b|\bwithdrawn\b|\bdeducted\b|\bsuccessful(?:ly)?\b|\bdeposited\b|\brefunded\b"""
    )

    private val DEBIT = Regex(
        """\bdebited\b|\bdebit (?:of|alert|transaction)\b|\bspent\b|\bpaid\b(?! you)|\bsent\b(?! you)|\btransferred\b|""" +
            """\btransfer (?:of|to)\b|\bfunds transfer\b|\bibft\b|\bpurchased?\b|\bpurchases\b|\bcharged\b|\bwithdrawn?\b|\bwithdrawal\b|""" +
            """\bdeducted\b|\bused (?:for|at)\b|\bpayment (?:of|to|for|successful|done|completed|made)\b|\bbill payment\b|""" +
            """\bpos\b|\btxn (?:at|of)\b|\btransaction (?:at|of)\b|\btop ?up\b|\brecharge\b|\bpaid successfully\b"""
    )

    private val CREDIT = Regex(
        """\bcredited\b|\bcredit (?:of|alert)\b|\byou (?:have )?received\b|\breceived (?:rs|pkr|₨)|\breceived from\b|""" +
            """\breceived in your\b|\bdeposited\b|\bsent you\b|\bpaid you\b|\btransferred (?:to|into) your\b|""" +
            """\brefund(?:ed)?\b|\breversed\b|\breversal\b|\bcash ?back (?:of )?(?:rs|pkr)?[\d,. ]*(?:has been )?credited\b|""" +
            """\binward\b|\bincoming\b|\bhas been added to your\b|\badded to your (?:wallet|account)\b"""
    )

    private val WEAK_DEBIT = Regex("""\border\b|\btrip\b|\bride\b|\btotal\b|\bfare\b|\breceipt\b|\bsubscription\b|\brenew(?:ed|al)\b|\bbilled\b|\binvoice\b""")

    private val BANK_MARKERS = Regex(
        """\ba/?c\b|\bacct?\b|\baccount\b|\bcard\b|\bwallet\b|\btxn\b|\btid\b|\btrx\b|\btrxid\b|\bref\b|\braast\b|\bibft\b|""" +
            """\bbal\b|\bbalance\b|\btransaction\b|\bdebited\b|\bcredited\b|\bpkr\b|easypaisa|jazzcash|sadapay|nayapay|\bbank\b|\bdebit\b|\bcredit\b|\bpos\b|\bstan\b"""
    )

    private val IGNORE_BEFORE = Regex("""\bbal\b|balance|\blimit\b|\bavl\b|\bavail\w*|available|outstanding|\bfee\b|\bfees\b|charges|\bfed\b|\btax\b|\bmin\b|\bdue\b|\bcashback of\b""")
    private val IGNORE_AFTER = Regex("""^\W{0,3}(?:is your (?:available )?bal|bal\b|balance|due\b|fee\b|charges)""")

    // ---------- public ----------

    fun parse(@Suppress("UNUSED_PARAMETER") pkg: String, label: String, title: String?, body: String, kind: AppKind, scope: Scope = Scope.SMART): Parsed? {
        if (kind == AppKind.SOCIAL) return null
        if (scope == Scope.FINANCE_ONLY && kind != AppKind.FINANCE && kind != AppKind.SMS) return null
        val text = normalize(listOfNotNull(title?.takeIf { it.isNotBlank() }, body.takeIf { it.isNotBlank() }).joinToString(". "))
        if (text.length < 6) return null
        val low = text.lowercase()
        if (HARD_REJECT.containsMatchIn(low)) return null
        if (kind == AppKind.SMS && !BANK_MARKERS.containsMatchIn(low) && SOURCES.none { it.second.containsMatchIn(title.orEmpty()) }) return null

        val amt = findAmount(text) ?: return null
        val strong = STRONG.containsMatchIn(low)
        if (PROMO.containsMatchIn(low) && !strong) return null
        if (kind == AppKind.OTHER && !strong) return null

        var flow = decideFlow(low, amt.second)
        if (flow == null) {
            if (kind == AppKind.MERCHANT && WEAK_DEBIT.containsMatchIn(low)) flow = Flow.OUT else return null
        }

        val source = detectSource(title, text, label, kind)
        val merchant = when (kind) {
            AppKind.MERCHANT -> if (flow == Flow.IN) cleanLabel(label) else cleanLabel(label)
            AppKind.OTHER -> extractMerchant(text, flow) ?: cleanLabel(label)
            else -> extractMerchant(text, flow)?.takeIf { merchantKey(it) != merchantKey(source) }
        }
        return Parsed(amt.first, flow, merchant, source)
    }

    fun normalize(s: String): String = s
        .replace(' ', ' ').replace(' ', ' ').replace('​', ' ')
        .replace(Regex("""[\r\n\t]+"""), " ")
        .replace(Regex(""" {2,}"""), " ")
        .trim()

    /** First money amount that isn't a balance, limit or fee. Returns paisa and its position. */
    fun findAmount(text: String): Pair<Long, Int>? {
        val hits = (AMOUNT_PREFIX.findAll(text) + AMOUNT_SUFFIX.findAll(text))
            .map { it to it.range.first }
            .sortedBy { it.second }
            .toList()
        for ((m, pos) in hits) {
            val before = clause(text.substring(maxOf(0, pos - 24), pos)).lowercase()
            if (IGNORE_BEFORE.containsMatchIn(before)) continue
            val after = text.substring(minOf(text.length, m.range.last + 1), minOf(text.length, m.range.last + 18)).lowercase()
            if (IGNORE_AFTER.containsMatchIn(after)) continue
            val v = toPaisa(m.groupValues[1]) ?: continue
            if (v <= 0 || v > 100_000_000_00L) continue
            return v to pos
        }
        return null
    }

    private fun clause(s: String): String {
        val cut = listOf(". ", "; ", ", ", ": ", "! ").maxOf { s.lastIndexOf(it).let { i -> if (i < 0) -1 else i + it.length } }
        return if (cut > 0) s.substring(cut) else s
    }

    private fun toPaisa(num: String): Long? {
        val t = num.replace(",", "")
        val parts = t.split('.')
        val whole = parts[0].toLongOrNull() ?: return null
        val frac = if (parts.size > 1) parts[1].padEnd(2, '0').take(2).toLongOrNull() ?: 0 else 0
        return whole * 100 + frac
    }

    private fun decideFlow(low: String, amountPos: Int): Flow? {
        val credits = CREDIT.findAll(low).map { it.range }.toList()
        val debits = DEBIT.findAll(low).map { it.range }.filter { d -> credits.none { c -> d.first <= c.last && c.first <= d.last } }.toList()
        if (credits.isEmpty() && debits.isEmpty()) return null
        if (credits.isEmpty()) return Flow.OUT
        if (debits.isEmpty()) return Flow.IN
        fun dist(r: IntRange) = if (r.last < amountPos) amountPos - r.last else r.first - amountPos
        val dc = credits.minOf { dist(it) }
        val dd = debits.minOf { dist(it) }
        return if (dc < dd) Flow.IN else Flow.OUT
    }

    // ---------- merchant ----------

    private const val NAME = """([A-Za-z0-9][A-Za-z0-9&'_\-* ]{0,44}?)"""
    private const val STOP =
        """(?=\s+(?:on|via|from|using|ref|tid|trx|txn|trxid|dated|at|with|for|card|a/c|ac|acct|account|avl|avail|bal|is|has|have|was|and|your|thru|through|ending|of|in|stan|id)\b|\s*[.,;:!()\[\]|]|\s+\d{1,2}[-/]\d|\s+\+?\d{4,}|\s+[x*]{2,}|\s*$)"""

    private val AT = Regex("""(?i)(?:\bat|@)\s+$NAME$STOP""")
    private val TO = Regex("""(?i)\bto\s+$NAME$STOP""")
    private val FOR = Regex("""(?i)\bfor\s+$NAME$STOP""")
    private val FROM = Regex("""(?i)\bfrom\s+$NAME$STOP""")
    private val BY = Regex("""(?i)\bby\s+$NAME$STOP""")
    private val MERCHANT_LABEL = Regex("""(?i)\b(?:merchant|payee|beneficiary|to name|sender)\s*(?:name)?\s*[:\-]\s*$NAME$STOP""")
    private val NAME_SENT_YOU = Regex("""(?i)([A-Za-z][A-Za-z ]{1,30}?)\s+(?:has\s+)?(?:sent|paid|transferred)\s+(?:you|to you)\b""")

    private val BAD_START = Regex(
        """(?i)^(?:your|you|yours|a/?c|ac|acc|acct|account|the|card|rs|pkr|mobile|wallet|number|bank|transaction|payment|this|our|via|""" +
            """ending|x+|\*+|\d+|own|self|my|other|an?|it|us|me|date|time|today|ref|tid|easypaisa account|jazzcash account|raast id)\b"""
    )

    fun extractMerchant(text: String, flow: Flow): String? {
        val order = if (flow == Flow.OUT) listOf(MERCHANT_LABEL, AT, TO, FOR) else listOf(MERCHANT_LABEL, NAME_SENT_YOU, FROM, BY)
        for (rx in order) {
            for (m in rx.findAll(text)) {
                val raw = m.groupValues[1].trim()
                if (raw.isEmpty() || BAD_START.containsMatchIn(raw)) continue
                val c = cleanMerchant(raw) ?: continue
                return c
            }
        }
        return null
    }

    private val CITIES = Regex(
        """(?i)\b(?:wah cantt|wah|cantt|islamabad|isb|rawalpindi|rwp|lahore|lhr|karachi|khi|peshawar|multan|faisalabad|taxila|""" +
            """quetta|hyderabad|sialkot|gujranwala|abbottabad|pk|pak|pakistan|pvt|ltd|limited|private|llc|inc|co)\b\.?"""
    )
    private val KEEP_CAPS = setOf("KFC", "PSO", "GT", "HBL", "UBL", "MCB", "PTCL", "SNGPL", "SSGC", "IESCO", "LESCO", "FESCO", "MEPCO", "PESCO", "GEPCO", "BBQ", "DHA", "ATM", "JLCPCB", "UK", "USA", "UAE", "TCS", "OPPO", "LG", "HP", "IKEA", "CTO", "ABL", "JS")

    fun cleanMerchant(raw: String): String? {
        var s = raw
            .replace(Regex("""\+?92\s?3\d{2}\s?\d{7}"""), " ")
            .replace(Regex("""\b0?3\d{2}[- ]?\d{7}\b"""), " ")
            .replace(Regex("""\b\d{4,}\b"""), " ")
            .replace(Regex("""[*#_]+"""), " ")
            .replace(Regex("""\bx{2,}\w*""", RegexOption.IGNORE_CASE), " ")
        s = CITIES.replace(s, " ")
        s = s.replace(Regex("""\s{2,}"""), " ").trim(' ', '-', '.', '\'', '&')
        if (s.length < 2 || s.none { it.isLetter() }) return null
        return titleCase(s).take(32).trim()
    }

    fun cleanLabel(label: String): String = label.trim().let { l ->
        if (l.isEmpty()) "Unknown" else if (l.lowercase() == l || l.uppercase() == l) titleCase(l) else l
    }

    fun titleCase(s: String): String = s.split(' ').filter { it.isNotEmpty() }.joinToString(" ") { word ->
        word.split('-').joinToString("-") { part ->
            val up = part.uppercase()
            when {
                up in KEEP_CAPS -> up
                part.length > 1 && part.none { it.lowercaseChar() in "aeiouy" } && part.all { it.isLetter() } -> up
                part.any { it.isLowerCase() } && part.any { it.isUpperCase() } -> part
                else -> part.lowercase().replaceFirstChar { it.uppercaseChar() }
            }
        }
    }

    // ---------- source ----------

    private val SOURCES = listOf(
        "JazzCash" to Regex("""(?i)jazz ?cash|mobilink"""),
        "Easypaisa" to Regex("""(?i)easy ?paisa|telenor ?bank"""),
        "SadaPay" to Regex("""(?i)sada ?pay"""),
        "NayaPay" to Regex("""(?i)naya ?pay"""),
        "Meezan" to Regex("""(?i)meezan"""),
        "HBL" to Regex("""(?i)\bhbl\b|habib bank"""),
        "UBL" to Regex("""(?i)\bubl\b|united bank"""),
        "MCB" to Regex("""(?i)\bmcb\b"""),
        "Alfalah" to Regex("""(?i)alfalah|\balfa\b"""),
        "Allied" to Regex("""(?i)\babl\b|allied bank"""),
        "Askari" to Regex("""(?i)askari"""),
        "Faysal" to Regex("""(?i)faysal"""),
        "BankIslami" to Regex("""(?i)bank ?islami"""),
        "JS Bank" to Regex("""(?i)\bjs ?bank\b"""),
        "HabibMetro" to Regex("""(?i)habib ?metro"""),
        "Bank Al Habib" to Regex("""(?i)al ?habib|\bbahl\b"""),
        "Standard Chartered" to Regex("""(?i)standard chartered|\bscb\b"""),
        "Dubai Islamic" to Regex("""(?i)dubai islamic|\bdib\b"""),
        "Silkbank" to Regex("""(?i)silk ?bank"""),
        "Zindigi" to Regex("""(?i)zindigi"""),
        "Payoneer" to Regex("""(?i)payoneer"""),
        "UPaisa" to Regex("""(?i)u ?paisa"""),
        "Raast" to Regex("""(?i)\braast\b"""),
    )

    fun detectSource(title: String?, text: String, label: String, kind: AppKind): String {
        for (probe in listOfNotNull(title, label, text)) {
            SOURCES.firstOrNull { it.second.containsMatchIn(probe) }?.let { return it.first }
        }
        if (kind == AppKind.SMS) {
            val t = title?.trim().orEmpty()
            return if (t.isNotEmpty() && t.length <= 14 && t.any { it.isLetter() }) titleCase(t) else "SMS"
        }
        return cleanLabel(label)
    }
}
