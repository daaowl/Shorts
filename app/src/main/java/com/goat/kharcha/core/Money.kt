package com.goat.kharcha.core

import kotlin.math.abs

object Money {
    /** "Rs 1,640" or "Rs 1,640.50". Negative values keep their sign in front. */
    fun format(paisa: Long, symbol: Boolean = true): String {
        val neg = paisa < 0
        val p = abs(paisa)
        val whole = p / 100
        val frac = p % 100
        val body = group(whole) + if (frac != 0L) "." + frac.toString().padStart(2, '0') else ""
        val s = if (symbol) "Rs $body" else body
        return if (neg) "−$s" else s
    }

    /** Whole rupees only, rounded. Used where decimals add noise. */
    fun round(paisa: Long, symbol: Boolean = true): String {
        val neg = paisa < 0
        val r = (abs(paisa) + 50) / 100
        val s = if (symbol) "Rs ${group(r)}" else group(r)
        return if (neg) "−$s" else s
    }

    /** Short labels for charts: 950, 12.4k, 1.2M. */
    fun compact(paisa: Long): String {
        val r = abs(paisa) / 100.0
        val s = when {
            r >= 1_000_000 -> trim(r / 1_000_000) + "M"
            r >= 1_000 -> trim(r / 1_000) + "k"
            else -> r.toLong().toString()
        }
        return if (paisa < 0) "−$s" else s
    }

    private fun trim(v: Double): String {
        val t = Math.round(v * 10) / 10.0
        return if (t == Math.floor(t)) t.toLong().toString() else t.toString()
    }

    fun group(n: Long): String {
        val s = n.toString()
        val sb = StringBuilder()
        for (i in s.indices) {
            if (i > 0 && (s.length - i) % 3 == 0) sb.append(',')
            sb.append(s[i])
        }
        return sb.toString()
    }

    /** Parses what a person typed ("1,640", "1640.5") into paisa. Null when it isn't a positive amount. */
    fun parseInput(text: String): Long? {
        val t = text.replace(",", "").trim()
        if (t.isEmpty() || t == ".") return null
        if (!Regex("""\d{0,9}(\.\d{0,2})?""").matches(t)) return null
        val parts = t.split('.')
        val whole = parts[0].ifEmpty { "0" }.toLong()
        val frac = if (parts.size > 1) parts[1].padEnd(2, '0').take(2).ifEmpty { "0" }.toLong() else 0L
        val v = whole * 100 + frac
        return if (v > 0) v else null
    }

    /** Paisa back to the editable form: "1640" or "1640.5". */
    fun toInput(paisa: Long): String {
        if (paisa <= 0) return ""
        val whole = paisa / 100
        val frac = paisa % 100
        return if (frac == 0L) whole.toString() else "$whole." + frac.toString().padStart(2, '0').trimEnd('0')
    }

    /** Spending expressed as hours of the person's own work. */
    fun hours(paisa: Long, ratePaisa: Long): String? {
        if (ratePaisa <= 0 || paisa <= 0) return null
        val h = paisa.toDouble() / ratePaisa
        return when {
            h < 1 -> "${Math.round(h * 60)} min of your work"
            h < 10 -> "${Math.round(h * 10) / 10.0} hours of your work"
            else -> "${Math.round(h)} hours of your work"
        }
    }
}
