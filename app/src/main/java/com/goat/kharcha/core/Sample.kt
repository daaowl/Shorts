package com.goat.kharcha.core

import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/** Two months of realistic example payments so every screen can be explored before real data arrives. */
object Sample {
    private data class Spot(val name: String, val cat: String, val lo: Int, val hi: Int, val src: String, val weight: Double, val mood: Mood)

    private val SPOTS = listOf(
        Spot("Cheezious", "food", 1200, 2600, "HBL", 3.0, Mood.REGRET),
        Spot("Foodpanda", "food", 900, 2200, "SadaPay + Foodpanda", 3.0, Mood.REGRET),
        Spot("Savour Foods", "food", 600, 1100, "Cash", 2.0, Mood.WORTH),
        Spot("Chai Shai", "food", 150, 400, "JazzCash", 4.0, Mood.WORTH),
        Spot("Imtiaz Super Market", "groceries", 2500, 6000, "Meezan", 1.2, Mood.WORTH),
        Spot("Al-Fatah", "groceries", 1500, 4500, "Meezan", 1.0, Mood.WORTH),
        Spot("PSO GT Road", "transport", 2500, 5000, "UBL", 1.2, Mood.WORTH),
        Spot("Careem", "transport", 350, 900, "JazzCash + Careem", 3.0, Mood.MEH),
        Spot("inDrive", "transport", 300, 800, "Cash", 2.0, Mood.WORTH),
        Spot("Daraz", "shopping", 900, 4500, "SadaPay + Daraz", 1.4, Mood.REGRET),
        Spot("Outfitters", "shopping", 2500, 6000, "HBL", 0.5, Mood.MEH),
        Spot("Servaid Pharmacy", "health", 400, 2200, "Cash", 0.8, Mood.WORTH),
        Spot("Cinepax", "fun", 900, 1800, "HBL", 0.5, Mood.WORTH),
        Spot("JLCPCB", "work", 2500, 8000, "SadaPay", 0.4, Mood.WORTH),
        Spot("Filament Store", "work", 2200, 4500, "Easypaisa", 0.4, Mood.WORTH),
    )

    fun generate(today: LocalDate, zone: ZoneId = ZoneId.systemDefault(), nowMillis: Long = System.currentTimeMillis()): List<Tx> {
        val r = Random(7)
        val out = ArrayList<Tx>()
        val totalW = SPOTS.sumOf { it.weight }
        val start = today.minusMonths(2).withDayOfMonth(1)
        fun at(d: LocalDate, h: Int): Long {
            val t = d.atTime(h, r.nextInt(0, 59)).atZone(zone).toInstant().toEpochMilli()
            return if (t > nowMillis) nowMillis - r.nextLong(30, 120) * 60_000 else t
        }
        fun add(d: LocalDate, h: Int, rupees: Int, name: String, cat: String?, src: String, mood: Mood, flow: Flow = Flow.OUT) {
            out += Tx(amount = (rupees / 10) * 10 * 100L, flow = flow, merchant = name, categoryId = cat, time = at(d, h), source = src, mood = mood, sample = true)
        }
        var d = start
        while (!d.isAfter(today)) {
            when (d.dayOfMonth) {
                1 -> { add(d, 11, 6000, "Ammi", "family", "Easypaisa", Mood.WORTH); add(d, 10, 145000, "Payoneer", "client", "Meezan", Mood.NONE, Flow.IN) }
                3 -> add(d, 10, 4500, "StormFiber", "bills", "HBL", Mood.WORTH)
                5 -> add(d, 9, 1100, "Netflix", "fun", "SadaPay", Mood.MEH)
                6 -> add(d, 12, 2600 + r.nextInt(0, 600), "IESCO", "bills", "JazzCash", Mood.NONE)
                8 -> add(d, 9, 299, "Spotify", "fun", "SadaPay", Mood.WORTH)
                12 -> add(d, 13, 1500 + r.nextInt(0, 400), "SNGPL", "bills", "JazzCash", Mood.NONE)
                18 -> add(d, 16, 62000, "Upwork client", "client", "Meezan", Mood.NONE, Flow.IN)
            }
            val n = if (r.nextDouble() < 0.3) 0 else 1 + if (r.nextDouble() < 0.25) 1 else 0
            repeat(n) {
                var p = r.nextDouble() * totalW
                var spot = SPOTS[0]
                for (s in SPOTS) { p -= s.weight; if (p <= 0) { spot = s; break } }
                val mood = when {
                    r.nextDouble() < 0.3 -> Mood.NONE
                    r.nextDouble() < 0.6 -> spot.mood
                    else -> Mood.values()[1 + r.nextInt(3)]
                }
                add(d, 9 + r.nextInt(13), spot.lo + r.nextInt(spot.hi - spot.lo), spot.name, spot.cat, spot.src, mood)
            }
            d = d.plusDays(1)
        }
        // One split dinner someone still owes, and two payments waiting for a reason.
        out.indexOfLast { it.merchant == "Cheezious" }.takeIf { it >= 0 }?.let { out[it] = out[it].copy(owed = true, owedBy = "Hamza", note = "Split dinner") }
        out += Tx(amount = 1640_00, merchant = "Foodpanda", time = nowMillis - 42 * 60_000, source = "SadaPay + Foodpanda", sample = true,
            raw = "SadaPay: You spent Rs 1,640 at FOODPANDA")
        out += Tx(amount = 1500_00, merchant = "Ali Hassan", time = nowMillis - 3 * 3_600_000, source = "Easypaisa", sample = true,
            raw = "Easypaisa: Rs 1,500 sent to ALI HASSAN. TID 884120.")
        return out
    }
}
