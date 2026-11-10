package com.goat.kharcha.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goat.kharcha.core.Cycle
import com.goat.kharcha.core.Flow
import com.goat.kharcha.core.Money
import com.goat.kharcha.core.Overview
import com.goat.kharcha.core.Stats
import com.goat.kharcha.data.Repo
import com.goat.kharcha.service.PaymentListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Ticks once a minute so "today" and the greeting stay correct if the app is left open overnight. */
@Composable
fun rememberToday(): LocalDate {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(60_000); now = System.currentTimeMillis() }
    }
    return remember(now / 60_000) { Cycle.date(now) }
}

/** Counts how many times the screen came back to the front, to re-check permissions changed elsewhere. */
@Composable
fun resumeTick(): Int {
    var n by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) n++
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return n
}

@Composable
fun listenerEnabled(): Boolean {
    val ctx = LocalContext.current
    val tick = resumeTick()
    return remember(tick) { PaymentListener.isEnabled(ctx) }
}

@Composable
fun bottomInset(): PaddingValues {
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return PaddingValues(start = 18.dp, end = 18.dp, top = top + 10.dp, bottom = nav + 116.dp)
}

@Composable
fun HomeScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val settings by Repo.settings.collectAsState()
    val txs by Repo.tx.collectAsState()
    val cats by Repo.categories.collectAsState()
    val today = rememberToday()
    val o = remember(txs, settings, today) { Stats.overview(txs, settings, today) }
    val listening = listenerEnabled()
    val state = rememberLazyListState()

    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = bottomInset()) {
        item(key = "head") {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting(settings.name), style = T.h1, color = P.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.US)), style = T.small, color = P.muted)
                }
                Row(
                    Modifier.clip(RoundedCornerShape(99.dp)).background(P.surface).border(1.dp, P.line, RoundedCornerShape(99.dp))
                        .press { nav.tab(Tab.SETTINGS) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (listening) P.good else P.bad))
                    Spacer(Modifier.width(8.dp))
                    Text(if (listening) "Watching" else "Paused", style = T.chip, color = P.fg)
                }
            }
        }

        if (!listening) item(key = "perm") { PermissionNudge() }

        item(key = "hero") { Hero(o, settings.hourlyRate) { nav.tab(Tab.SETTINGS) } }

        if (o.pending > 0) item(key = "pending") {
            val first = txs.firstOrNull { it.needsTag && it.flow == Flow.OUT }
            Row(
                Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).press { nav.push(Page.QUEUE) }
                    .background(P.warn.copy(alpha = if (P.dark) 0.11f else 0.10f))
                    .border(1.dp, P.warn.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CatIcon(null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${o.pending} payment${if (o.pending == 1) "" else "s"} need${if (o.pending == 1) "s" else ""} a reason", style = T.title, color = P.fg)
                    if (first != null) Text("Latest: ${Money.format(first.amount)} · ${first.payee}", style = T.small, color = P.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("Tag now", style = T.chip, color = P.warn)
            }
        }

        item(key = "tiles") {
            Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniTile("Today", Money.round(o.spentToday), Modifier.weight(1f))
                MiniTile("Last 7 days", Money.round(o.spentWeek), Modifier.weight(1f))
                if (o.income > 0) MiniTile("Came in", Money.round(o.income), Modifier.weight(1f), P.good)
                else MiniTile("Payments", o.byCategory.sumOf { it.count }.toString(), Modifier.weight(1f))
            }
        }

        if (o.upcoming.isNotEmpty()) {
            item(key = "upc-title") { SectionTitle("Set aside for bills") }
            item(key = "upc") {
                Card(padding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                    o.upcoming.take(4).forEachIndexed { i, r ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(P.line))
                        val days = ChronoUnit.DAYS.between(today, r.next).toInt()
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            CatIcon(Repo.category(r.categoryId), size = 34.dp, iconSize = 18.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.merchant, style = T.title, color = P.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    when { days <= 0 -> "Due about now"; days == 1 -> "Expected tomorrow"; else -> "Expected in $days days" },
                                    style = T.small, color = if (days <= 1) P.warn else P.muted,
                                )
                            }
                            Text(Money.format(r.amount, false), style = T.num, color = P.fg)
                        }
                    }
                }
            }
        }

        val catRows = o.byCategory.filter { it.amount > 0 }
        if (catRows.isNotEmpty()) {
            item(key = "cats-title") { SectionTitle("Where it went", "Insights") { nav.tab(Tab.INSIGHTS) } }
            items(catRows, key = { "cat-" + (it.categoryId ?: "none") }) { row ->
                val cat = cats.firstOrNull { it.id == row.categoryId }
                val budget = cat?.budget ?: 0L
                val frac = if (budget > 0) row.amount.toFloat() / budget else if (o.spent > 0) row.amount.toFloat() / o.spent else 0f
                val over = budget > 0 && row.amount > budget
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    CatIcon(cat)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(cat?.name ?: "Needs a reason", style = T.title, color = P.fg, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Money.format(row.amount, false), style = T.num, color = P.fg)
                        }
                        Meter(frac, if (over) P.bad else if (cat == null) P.warn else catColor(cat.color, P.dark), height = 5.dp)
                        Text(
                            when {
                                budget > 0 && over -> "${Money.round(row.amount - budget)} over the ${Money.round(budget)} budget"
                                budget > 0 -> "${Money.round(budget - row.amount)} left of ${Money.round(budget)}"
                                else -> "${Math.round(frac * 100)}% of spending · ${row.count} payment${if (row.count == 1) "" else "s"}"
                            },
                            style = T.small, color = if (over) P.bad else if (budget > 0 && frac > 0.85f) P.warn else P.muted,
                        )
                    }
                }
            }
        }

        val recent = txs.take(6)
        if (recent.isNotEmpty()) {
            item(key = "recent-title") { SectionTitle("Recent", "See all") { nav.tab(Tab.ACTIVITY) } }
            items(recent, key = { "r-${it.id}" }) { t -> TxRow(t, cats) { nav.edit(t) } }
        } else {
            item(key = "empty") { EmptyHome() }
        }

        if (txs.any { it.sample }) item(key = "sample") {
            Text(
                "Showing sample payments. Remove them in Settings › Data.",
                style = T.small, color = P.faint, modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Hero(o: Overview, rate: Long, onSetBudget: () -> Unit) {
    val accent = P.accent
    var w by remember { mutableStateOf(1f) }
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(P.surface)
            .onSizeChanged { w = it.width.toFloat() }
            .drawBehind {
                drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.26f), Color.Transparent), center = Offset(size.width * 1.02f, -size.height * 0.05f), radius = w * 0.95f))
                drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.07f), Color.Transparent), center = Offset(0f, size.height), radius = w * 0.7f))
            }
            .border(1.dp, P.line, shape)
            .padding(horizontal = 22.dp, vertical = 22.dp),
    ) {
        if (o.hasBudget) {
            val over = o.safeToday < 0
            Eyebrow(if (over) "Over today's share by" else "Safe to spend today", color = if (over) P.bad else P.muted)
            AnimatedAmount(if (over) -o.safeToday else o.safeToday, color = if (over) P.bad else P.fg)
            Text(
                "${o.daysLeft} day${if (o.daysLeft == 1) "" else "s"} left in ${o.range.label()} · " +
                    (if (o.left >= 0) "${Money.round(o.left)} of ${Money.round(o.budget)} left" else "${Money.round(-o.left)} over budget"),
                style = T.small, color = P.muted, modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
            Meter(o.progress, if (o.spent > o.budget) P.bad else accent, marker = o.expected)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("Spent ${Money.round(o.spent)}", style = T.small, color = P.muted, modifier = Modifier.weight(1f))
                if (o.dayIndex < 4) {
                    Text("Day ${o.dayIndex} of ${o.daysTotal}", style = T.small, color = P.muted)
                } else {
                    val ahead = o.forecast <= o.budget
                    Text(
                        "On pace for ${Money.round(o.forecast)}", style = T.small.copy(fontWeight = FontWeight.Bold),
                        color = if (ahead) P.good else P.bad,
                    )
                }
            }
            if (o.reserved > 0) {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph("repeat", P.accent, 15.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "${Money.round(o.reserved)} held back for ${o.upcoming.size} upcoming bill${if (o.upcoming.size == 1) "" else "s"}",
                        style = T.small, color = P.accent,
                    )
                }
            }
        } else {
            Eyebrow("Spent in ${o.range.label()}")
            AnimatedAmount(o.spent, color = P.fg)
            Text(
                "Set a monthly budget and Kharcha works out what's safe to spend each day.",
                style = T.small, color = P.muted, modifier = Modifier.padding(top = 4.dp),
            )
            Text("Set budget", style = T.chip, color = accent, modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(8.dp)).press(onClick = onSetBudget).padding(vertical = 4.dp))
        }
        Money.hours(o.spent, rate)?.let {
            Text("This cycle cost you ${it.removeSuffix(" of your work")} of work", style = T.small, color = P.faint, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
fun AnimatedAmount(paisa: Long, color: Color) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val v by animateFloatAsState(if (started) paisa / 100f else 0f, tween(900, easing = FastOutSlowInEasing), label = "amount")
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
        Text("Rs", style = T.h2.copy(fontWeight = FontWeight.SemiBold), color = P.muted, modifier = Modifier.padding(bottom = 10.dp, end = 8.dp))
        Text(Money.group(Math.round(v.toDouble())), style = T.hero, color = color, maxLines = 1)
    }
}

@Composable
fun MiniTile(label: String, value: String, modifier: Modifier = Modifier, color: Color = P.fg) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(P.surface).border(1.dp, P.line, RoundedCornerShape(18.dp)).padding(horizontal = 13.dp, vertical = 12.dp),
    ) {
        Text(label, style = T.small, color = P.muted, maxLines = 1)
        Text(value, style = T.stat.copy(fontSize = T.stat.fontSize * 0.78f), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun PermissionNudge() {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    Row(
        Modifier.padding(bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .press { nav.activity?.markExternalTrip(); Permissions.openListenerSettings(ctx) }
            .background(P.bad.copy(alpha = 0.10f)).border(1.dp, P.bad.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph("bell", P.bad, 22.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Payment detection is off", style = T.title, color = P.fg)
            Text("Allow notification access so Kharcha can catch payments", style = T.small, color = P.muted)
        }
        Text("Turn on", style = T.chip, color = P.bad)
    }
}

@Composable
private fun EmptyHome() {
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    Card(Modifier.padding(top = 28.dp).fillMaxWidth(), padding = PaddingValues(22.dp)) {
        Glyph("wallet", P.accent, 30.dp)
        Text("Waiting for your first payment", style = T.h2, color = P.fg, modifier = Modifier.padding(top = 14.dp))
        Text(
            "Pay with any bank, wallet or app as usual. Kharcha reads the payment notification and asks what it was for.",
            style = T.body, color = P.muted, modifier = Modifier.padding(top = 6.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Send a test", Modifier.weight(1f)) {
                TestPayment.send(ctx)
                toaster.show("Test payment sent. Check your notifications.")
            }
            SecondaryButton("Sample data", Modifier.weight(1f)) {
                Repo.scope.launch { Repo.loadSample() }
                toaster.show("Loaded two months of sample payments")
            }
        }
    }
}
