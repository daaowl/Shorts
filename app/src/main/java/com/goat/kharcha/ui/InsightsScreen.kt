package com.goat.kharcha.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goat.kharcha.core.Cycle
import com.goat.kharcha.core.Flow
import com.goat.kharcha.core.Money
import com.goat.kharcha.core.Mood
import com.goat.kharcha.core.Stats
import com.goat.kharcha.data.Repo
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun InsightsScreen() {
    val nav = LocalNav.current
    val toaster = LocalToaster.current
    val txs by Repo.tx.collectAsState()
    val cats by Repo.categories.collectAsState()
    val settings by Repo.settings.collectAsState()
    val today = rememberToday()
    val o = remember(txs, settings, today) { Stats.overview(txs, settings, today) }
    val sums = remember(txs, o.range) { Stats.daySums(txs, o.range) }
    val todayIdx = ChronoUnit.DAYS.between(o.range.start, today).toInt()
    val weekday = remember(txs, today) { Stats.weekdayAverages(txs, today) }
    val trend = remember(txs, settings.cycleDay, today) { Stats.trend(txs, settings.cycleDay, today) }
    val regret = remember(txs) { Stats.regret(txs, null) }
    val recurring = remember(txs, today) { Stats.recurring(txs, today) }
    val top = remember(txs, o.range) { Stats.topMerchants(txs, o.range) }
    val owed = remember(txs) { txs.filter { it.owed } }

    if (txs.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(bottomInset())) {
            ScreenHeader("Insights")
            Card(Modifier.fillMaxWidth()) {
                Glyph("chart", P.accent, 28.dp)
                Text("Patterns show up after a few payments", style = T.h2, color = P.fg, modifier = Modifier.padding(top = 12.dp))
                Text("Your spending calendar, most expensive weekday, regrets and recurring bills will appear here.", style = T.body, color = P.muted, modifier = Modifier.padding(top = 6.dp))
            }
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize(), state = rememberLazyListState(), contentPadding = bottomInset()) {
        item(key = "head") { ScreenHeader("Insights", o.range.label()) }

        if (settings.hourlyRate > 0) item(key = "hours") {
            val h = o.spent.toDouble() / settings.hourlyRate
            Card(Modifier.fillMaxWidth()) {
                Eyebrow("This cycle in hours of your work")
                Text("${if (h < 10) Math.round(h * 10) / 10.0 else Math.round(h)} hours", style = T.h1.copy(fontSize = T.h1.fontSize * 1.15f), color = P.fg, modifier = Modifier.padding(top = 6.dp))
                Text(
                    "That's ${Math.round(h / 8 * 10) / 10.0} full working days at ${Money.round(settings.hourlyRate)} an hour." +
                        if (o.byCategory.firstOrNull()?.categoryId != null) {
                            val c = o.byCategory.first()
                            " ${Repo.category(c.categoryId)?.name ?: "Top category"} alone took ${Math.round(c.amount.toDouble() / settings.hourlyRate)} hours."
                        } else "",
                    style = T.body, color = P.muted, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        item(key = "cal") {
            SectionTitle("Spending calendar")
            val streak = Stats.streak(sums, todayIdx, o.dailyLimit)
            val zeroDays = (0..todayIdx.coerceAtMost(sums.lastIndex)).count { sums[it] == 0L }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(if (o.dailyLimit > 0) "$streak" else "–", if (o.dailyLimit > 0) "day streak under ${Money.round(o.dailyLimit)}" else "Set a budget to track streaks", Modifier.weight(1f))
                StatTile("$zeroDays", "no-spend day${if (zeroDays == 1) "" else "s"} this cycle", Modifier.weight(1f), P.good)
            }
            Spacer(Modifier.height(12.dp))
            CalendarHeat(o.range.start, sums, todayIdx)
        }

        item(key = "week") {
            SectionTitle("Your expensive day")
            val maxI = weekday.indices.maxByOrNull { weekday[it] } ?: 0
            if (weekday[maxI] > 0) {
                Text(
                    "${Stats.dayName(maxI)}s cost you the most: about ${Money.round(weekday[maxI])} on average over the last 8 weeks.",
                    style = T.body, color = P.muted,
                )
            }
            Spacer(Modifier.height(12.dp))
            Bars(
                values = weekday.toList(),
                labels = listOf("M", "T", "W", "T", "F", "S", "S"),
                highlight = maxI,
            )
        }

        item(key = "trend") {
            SectionTitle("Cycle by cycle")
            val cur = trend.lastOrNull()?.second ?: 0
            val prev = trend.getOrNull(trend.size - 2)?.second ?: 0
            if (prev > 0) {
                val elapsed = o.dayIndex.toFloat() / o.daysTotal
                val pace = (prev * elapsed).toLong()
                val diff = cur - pace
                Text(
                    if (diff <= 0) "${Money.round(-diff)} less than at this point last cycle." else "${Money.round(diff)} more than at this point last cycle.",
                    style = T.body, color = if (diff <= 0) P.good else P.bad,
                )
            }
            Spacer(Modifier.height(12.dp))
            Bars(
                values = trend.map { it.second },
                labels = trend.map { it.first.shortLabel() },
                highlight = trend.lastIndex,
                line = settings.budget.takeIf { it > 0 },
                showValues = true,
            )
        }

        item(key = "regret") {
            SectionTitle("Regret meter")
            if (regret.isEmpty()) {
                Text("Mark payments as Worth it, Meh or Regret when you tag them. Kharcha shows where the regret piles up.", style = T.body, color = P.muted)
            } else {
                val total = txs.filter { it.mood == Mood.REGRET && Cycle.date(it.time) in o.range }.sumOf { it.amount }
                Text("${Money.round(total)} this cycle went on things you'd take back.", style = T.body, color = P.muted)
                Spacer(Modifier.height(8.dp))
                regret.take(5).forEach { r ->
                    val c = cats.firstOrNull { it.id == r.categoryId }
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        CatIcon(c, size = 34.dp, iconSize = 18.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row {
                                Text(c?.name ?: "Other", style = T.title, color = P.fg, modifier = Modifier.weight(1f))
                                Text("${Math.round(r.share * 100)}%", style = T.num, color = P.bad)
                            }
                            Meter(r.share, P.bad, height = 5.dp)
                            Text("${Money.round(r.regret)} regretted of ${Money.round(r.rated)} rated", style = T.small, color = P.muted)
                        }
                    }
                }
            }
        }

        if (recurring.isNotEmpty()) item(key = "rec") {
            SectionTitle("Recurring payments")
            Text("${Money.round(recurring.sumOf { it.amount })} a month, found from repeat payments.", style = T.body, color = P.muted)
            Spacer(Modifier.height(8.dp))
            val f = DateTimeFormatter.ofPattern("d MMM", Locale.US)
            recurring.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CatIcon(Repo.category(r.categoryId), size = 34.dp, iconSize = 18.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.merchant, style = T.title, color = P.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Next around ${r.next.format(f)} · seen ${r.count}×", style = T.small, color = P.muted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Money.format(r.amount, false), style = T.num, color = P.fg)
                        Text("/month", style = T.numSmall, color = P.muted)
                    }
                }
            }
        }

        if (top.isNotEmpty()) item(key = "top") {
            SectionTitle("Top places this cycle")
            val max = top.maxOf { it.amount }.coerceAtLeast(1)
            top.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    CatIcon(Repo.category(m.categoryId), size = 34.dp, iconSize = 18.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(m.name, style = T.title, color = P.fg, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Money.format(m.amount, false), style = T.num, color = P.fg)
                        }
                        Meter(m.amount.toFloat() / max, Repo.category(m.categoryId)?.let { catColor(it.color, P.dark) } ?: P.muted, height = 5.dp)
                        Text("${m.count} payment${if (m.count == 1) "" else "s"}", style = T.small, color = P.muted)
                    }
                }
            }
        }

        item(key = "owed") {
            SectionTitle("Owed to you")
            if (owed.isEmpty()) {
                Text("Nobody owes you. Switch on \"Someone owes me\" when you tag a payment to keep track.", style = T.body, color = P.muted)
            } else {
                Text("${Money.round(owed.sumOf { it.amount })} still out there.", style = T.body, color = P.muted)
                Spacer(Modifier.height(8.dp))
                owed.forEach { t ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(P.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            Text(t.owedBy.ifBlank { "?" }.take(1).uppercase(), style = T.title, color = P.accent)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f).clickable { nav.edit(t) }) {
                            Text(t.owedBy.ifBlank { "Someone" }, style = T.title, color = P.fg)
                            Text("${Money.round(t.amount)} · ${t.payee} · ${dateOf(t.time)}", style = T.small, color = P.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            "Paid back", style = T.chip, color = P.good,
                            modifier = Modifier.clip(RoundedCornerShape(99.dp)).border(1.dp, P.good.copy(alpha = 0.5f), RoundedCornerShape(99.dp))
                                .clickable {
                                    Repo.scope.launch { Repo.save(t.copy(owed = false)) }
                                    toaster.show("${t.owedBy.ifBlank { "They" }} paid you back ${Money.round(t.amount)}", "Undo") {
                                        Repo.scope.launch { Repo.save(t) }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier, color: Color = P.fg) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(P.surface).border(1.dp, P.line, RoundedCornerShape(18.dp)).padding(14.dp)) {
        Text(value, style = T.stat, color = color)
        Text(label, style = T.small, color = P.muted, maxLines = 2)
    }
}

@Composable
private fun CalendarHeat(start: LocalDate, sums: LongArray, todayIdx: Int) {
    var picked by remember { mutableStateOf(todayIdx.coerceIn(0, sums.lastIndex.coerceAtLeast(0))) }
    val max = (0..todayIdx.coerceAtMost(sums.lastIndex)).maxOfOrNull { sums[it] }?.coerceAtLeast(1L) ?: 1L
    val offset = start.dayOfWeek.value - 1
    val cells = offset + sums.size
    val rows = (cells + 6) / 7
    val accent = P.accent
    Card(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, style = T.label, color = P.faint, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(8.dp))
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (c in 0 until 7) {
                    val i = r * 7 + c - offset
                    if (i < 0 || i >= sums.size) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val d = start.plusDays(i.toLong())
                        val future = i > todayIdx
                        val v = sums[i]
                        val level = if (future || v == 0L) 0f else (0.18f + 0.82f * (v.toFloat() / max))
                        val bg = when {
                            future -> P.surface2.copy(alpha = 0.5f)
                            v == 0L -> Color.Transparent
                            else -> accent.copy(alpha = level)
                        }
                        val text = when {
                            future -> P.faint
                            v == 0L -> P.good
                            level > 0.6f -> P.onAccent
                            else -> P.fg
                        }
                        val shape = RoundedCornerShape(9.dp)
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(shape)
                                .background(bg)
                                .then(if (!future && v == 0L) Modifier.border(1.5.dp, P.good.copy(alpha = 0.7f), shape) else Modifier)
                                .then(if (i == picked) Modifier.border(2.dp, P.fg, shape) else Modifier)
                                .clickable(enabled = !future) { picked = i },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(d.dayOfMonth.toString(), style = T.numSmall, color = text)
                        }
                    }
                }
            }
        }
        val pd = start.plusDays(picked.toLong())
        val n = Repo.tx.value.count { it.flow == Flow.OUT && Cycle.date(it.time) == pd }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(pd.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.US)), style = T.small.copy(fontWeight = FontWeight.Bold), color = P.fg, modifier = Modifier.weight(1f))
            Text(
                if (sums.getOrElse(picked) { 0 } == 0L) "Spent nothing" else "${Money.round(sums[picked])} · $n payment${if (n == 1) "" else "s"}",
                style = T.small, color = if (sums.getOrElse(picked) { 0 } == 0L) P.good else P.muted,
            )
        }
    }
}

/** Simple bar chart drawn to scale, with an optional dashed reference line. */
@Composable
private fun Bars(values: List<Long>, labels: List<String>, highlight: Int, line: Long? = null, showValues: Boolean = false) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val grow by animateFloatAsState(if (started) 1f else 0f, tween(800), label = "bars")
    val top = maxOf(values.maxOrNull() ?: 0L, line ?: 0L).coerceAtLeast(1L)
    val accent = P.accent
    val soft = accent.copy(alpha = 0.32f)
    val lineColor = P.muted
    Card(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
        if (showValues) {
            Row(Modifier.fillMaxWidth()) {
                values.forEachIndexed { i, v ->
                    Text(
                        if (v > 0) Money.compact(v) else "", style = T.numSmall, color = if (i == highlight) P.fg else P.muted,
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Box(Modifier.fillMaxWidth().height(120.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val n = values.size
                if (n == 0) return@Canvas
                val slot = size.width / n
                val bw = slot * 0.56f
                values.forEachIndexed { i, v ->
                    val h = (v.toFloat() / top) * size.height * grow
                    val x = slot * i + (slot - bw) / 2
                    drawRoundRect(
                        color = if (i == highlight) accent else soft,
                        topLeft = Offset(x, size.height - h.coerceAtLeast(3f)),
                        size = Size(bw, h.coerceAtLeast(3f)),
                        cornerRadius = CornerRadius(8f, 8f),
                    )
                }
                if (line != null) {
                    val y = size.height - (line.toFloat() / top) * size.height
                    drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, l ->
                Text(l, style = T.label, color = if (i == highlight) P.fg else P.faint, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1)
            }
        }
        if (line != null) {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(14.dp).height(2.dp).background(lineColor))
                Spacer(Modifier.width(8.dp))
                Text("Budget ${Money.round(line)}", style = T.small, color = P.muted)
            }
        }
    }
}
