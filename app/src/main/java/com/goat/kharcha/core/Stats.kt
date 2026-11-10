package com.goat.kharcha.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max

data class Recurring(
    val merchant: String,
    val amount: Long,
    val next: LocalDate,
    val categoryId: String?,
    val count: Int,
)

data class CatSpend(val categoryId: String?, val amount: Long, val count: Int)

data class Overview(
    val range: CycleRange,
    val today: LocalDate,
    val dayIndex: Int,
    val daysTotal: Int,
    val daysLeft: Int,
    val budget: Long,
    val spent: Long,
    val spentToday: Long,
    val spentWeek: Long,
    val income: Long,
    val reserved: Long,
    val upcoming: List<Recurring>,
    val safeToday: Long,
    val forecast: Long,
    val dailyLimit: Long,
    val byCategory: List<CatSpend>,
    val pending: Int,
) {
    val hasBudget: Boolean get() = budget > 0
    val left: Long get() = budget - spent
    val progress: Float get() = if (budget > 0) (spent.toFloat() / budget).coerceIn(0f, 1.5f) else 0f
    val expected: Float get() = dayIndex.toFloat() / daysTotal
}

data class RegretRow(val categoryId: String, val regret: Long, val rated: Long) {
    val share: Float get() = if (rated > 0) regret.toFloat() / rated else 0f
}

data class MerchantRow(val name: String, val amount: Long, val count: Int, val categoryId: String?)

object Stats {
    private fun zone(): ZoneId = ZoneId.systemDefault()

    fun overview(txs: List<Tx>, s: Settings, today: LocalDate): Overview {
        val z = zone()
        val range = Cycle.of(today, s.cycleDay)
        val dated = txs.map { it to Cycle.date(it.time, z) }
        val inCycle = dated.filter { it.second in range }
        val out = inCycle.filter { it.first.flow == Flow.OUT }
        val spent = out.sumOf { it.first.amount }
        val spentToday = out.filter { it.second == today }.sumOf { it.first.amount }
        val weekStart = today.minusDays(6)
        val spentWeek = dated.filter { it.first.flow == Flow.OUT && !it.second.isBefore(weekStart) && !it.second.isAfter(today) }.sumOf { it.first.amount }
        val income = inCycle.filter { it.first.flow == Flow.IN }.sumOf { it.first.amount }

        val daysTotal = range.days
        val dayIndex = ChronoUnit.DAYS.between(range.start, today).toInt() + 1
        val daysLeft = max(1, ChronoUnit.DAYS.between(today, range.end).toInt())

        val upcoming = recurring(txs, today)
            .mapNotNull { r ->
                when {
                    !r.next.isBefore(today) && r.next.isBefore(range.end) -> r
                    r.next.isBefore(today) && !r.next.isBefore(today.minusDays(5)) -> r.copy(next = today)
                    else -> null
                }
            }
            .sortedBy { it.next }
        val reserved = upcoming.sumOf { it.amount }

        val safe = if (s.budget > 0) (s.budget - (spent - spentToday) - reserved) / daysLeft - spentToday else 0L
        val forecast = if (dayIndex > 0) spent * daysTotal / dayIndex else spent

        val byCat = out.groupBy { it.first.categoryId }
            .map { (k, v) -> CatSpend(k, v.sumOf { it.first.amount }, v.size) }
            .sortedByDescending { it.amount }
        val pending = txs.count { it.flow == Flow.OUT && it.needsTag }

        return Overview(
            range, today, dayIndex, daysTotal, daysLeft, s.budget, spent, spentToday, spentWeek, income,
            reserved, upcoming, safe, forecast, if (daysTotal > 0) s.budget / daysTotal else 0, byCat, pending,
        )
    }

    /** Payments to the same place for a similar amount about a month apart, at least twice in a row. */
    fun recurring(txs: List<Tx>, today: LocalDate): List<Recurring> {
        val z = zone()
        val horizon = today.minusMonths(7)
        val groups = txs
            .filter { it.flow == Flow.OUT && !it.merchant.isNullOrBlank() && !Cycle.date(it.time, z).isBefore(horizon) }
            .groupBy { merchantKey(it.merchant) }
            .filterKeys { it.length >= 2 }
        val result = ArrayList<Recurring>()
        for ((_, list) in groups) {
            if (list.size < 2) continue
            val sorted = list.sortedBy { it.time }
            val last = sorted.last()
            val tol = if (last.categoryId == "bills") 0.40 else 0.12
            val similar = sorted.filter { abs(it.amount - last.amount) <= max(50_00L, (last.amount * tol).toLong()) }
            if (similar.size < 2 || sorted.size > similar.size + 1) continue
            val dates = similar.map { Cycle.date(it.time, z) }
            val gaps = dates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }
            if (gaps.isEmpty() || gaps.any { it !in 25..36 }) continue
            val avg = gaps.average().let { Math.round(it) }
            val next = dates.last().plusDays(avg)
            if (next.isBefore(today.minusDays(10))) continue
            result += Recurring(last.payee, last.amount, next, last.categoryId, similar.size)
        }
        return result.sortedBy { it.next }
    }

    /** Spending per day of the cycle, index 0 = first day. */
    fun daySums(txs: List<Tx>, range: CycleRange): LongArray {
        val z = zone()
        val arr = LongArray(range.days)
        for (t in txs) {
            if (t.flow != Flow.OUT) continue
            val d = Cycle.date(t.time, z)
            if (d in range) arr[ChronoUnit.DAYS.between(range.start, d).toInt()] += t.amount
        }
        return arr
    }

    /** Days in a row, ending today, that stayed at or under the daily limit. */
    fun streak(sums: LongArray, todayIndex: Int, limit: Long): Int {
        if (limit <= 0) return 0
        var n = 0
        var i = todayIndex
        while (i >= 0 && i < sums.size && sums[i] <= limit) { n++; i-- }
        return n
    }

    /** Average spend per weekday over the last [weeks] weeks, Monday first. */
    fun weekdayAverages(txs: List<Tx>, today: LocalDate, weeks: Int = 8): LongArray {
        val z = zone()
        val from = today.minusDays(weeks * 7L - 1)
        val sums = LongArray(7)
        val counts = IntArray(7)
        var d = from
        while (!d.isAfter(today)) { counts[d.dayOfWeek.value - 1]++; d = d.plusDays(1) }
        for (t in txs) {
            if (t.flow != Flow.OUT) continue
            val td = Cycle.date(t.time, z)
            if (td.isBefore(from) || td.isAfter(today)) continue
            sums[td.dayOfWeek.value - 1] += t.amount
        }
        return LongArray(7) { if (counts[it] > 0) sums[it] / counts[it] else 0 }
    }

    fun dayName(index: Int): String = DayOfWeek.of(index + 1).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US)

    /** Total spent in each of the last [n] cycles, oldest first, current cycle last. */
    fun trend(txs: List<Tx>, cycleDay: Int, today: LocalDate, n: Int = 6): List<Pair<CycleRange, Long>> {
        val z = zone()
        val ranges = ArrayList<CycleRange>()
        var r = Cycle.of(today, cycleDay)
        repeat(n) { ranges.add(0, r); r = r.previous() }
        val out = txs.filter { it.flow == Flow.OUT }.map { it to Cycle.date(it.time, z) }
        return ranges.map { rg -> rg to out.filter { it.second in rg }.sumOf { it.first.amount } }
    }

    fun regret(txs: List<Tx>, range: CycleRange?): List<RegretRow> {
        val z = zone()
        return txs
            .filter { it.flow == Flow.OUT && it.mood != Mood.NONE && it.categoryId != null && (range == null || Cycle.date(it.time, z) in range) }
            .groupBy { it.categoryId!! }
            .map { (k, v) -> RegretRow(k, v.filter { it.mood == Mood.REGRET }.sumOf { it.amount }, v.sumOf { it.amount }) }
            .filter { it.regret > 0 }
            .sortedByDescending { it.regret }
    }

    fun topMerchants(txs: List<Tx>, range: CycleRange, n: Int = 5): List<MerchantRow> {
        val z = zone()
        return txs
            .filter { it.flow == Flow.OUT && Cycle.date(it.time, z) in range && !it.merchant.isNullOrBlank() }
            .groupBy { merchantKey(it.merchant) }
            .map { (_, v) ->
                val name = v.groupingBy { it.payee }.eachCount().maxByOrNull { it.value }!!.key
                val cat = v.mapNotNull { it.categoryId }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                MerchantRow(name, v.sumOf { it.amount }, v.size, cat)
            }
            .sortedByDescending { it.amount }
            .take(n)
    }
}
