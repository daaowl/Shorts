package com.goat.kharcha.core

import kotlin.math.abs

/**
 * One payment often shows up twice: the bank's text and the shop's own app.
 * Decides whether an incoming detection is new, a repeat of the same notification, or the
 * other half of a payment already logged, so it's counted once.
 */
object Linker {
    sealed interface Decision {
        data object Skip : Decision
        data class Insert(val tx: Tx) : Decision
        data class Merge(val tx: Tx) : Decision
    }

    private const val REPEAT_WINDOW = 24L * 60 * 60 * 1000
    private const val SHORT_REPEAT_WINDOW = 3L * 60 * 1000
    private const val LINK_WINDOW = 20L * 60 * 1000

    private val AMOUNTS = Regex("""(?i)(?:pkr|rs\.?|₨)\s*[\d,]+(?:\.\d+)?|[\d,]+(?:\.\d+)?\s*(?:pkr|rs)\b""")
    private val TOKEN = Regex("""\d{4,}|\d{1,3}(?:,\d{2,3})+""")

    fun hasUniqueToken(raw: String): Boolean = TOKEN.containsMatchIn(AMOUNTS.replaceFirst(raw, " "))

    fun decide(existing: List<Tx>, incoming: Tx, pkg: String, fromMerchantApp: Boolean): Decision {
        val rawKey = incoming.raw.trim()
        // The same text again is a re-post (app update, reboot) unless two identical purchases are plausible.
        // Texts with a reference number or balance can never legitimately repeat, so they get a full day.
        val window = if (hasUniqueToken(rawKey)) REPEAT_WINDOW else SHORT_REPEAT_WINDOW
        val repeat = existing.any {
            !it.manual && pkg in it.packageList && abs(it.time - incoming.time) < window &&
                it.raw.split(SEP).any { r -> r.trim() == rawKey }
        }
        if (repeat) return Decision.Skip

        val twin = existing
            .filter {
                !it.manual && !it.sample && it.flow == incoming.flow && it.amount == incoming.amount &&
                    abs(it.time - incoming.time) <= LINK_WINDOW && pkg !in it.packageList
            }
            .minByOrNull { abs(it.time - incoming.time) }
            ?: return Decision.Insert(incoming)

        val merchant = when {
            twin.merchant.isNullOrBlank() -> incoming.merchant
            fromMerchantApp && !incoming.merchant.isNullOrBlank() -> incoming.merchant
            else -> twin.merchant
        }
        val sources = (twin.source.split(" + ") + incoming.source).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return Decision.Merge(
            twin.copy(
                merchant = merchant,
                source = sources.joinToString(" + "),
                packages = (twin.packageList + pkg).distinct().joinToString(","),
                raw = listOf(twin.raw, incoming.raw).filter { it.isNotBlank() }.joinToString(SEP),
                time = minOf(twin.time, incoming.time),
            )
        )
    }

    const val SEP = "\n⁣\n"
}
