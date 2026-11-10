package com.goat.kharcha.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.goat.kharcha.core.Cycle
import com.goat.kharcha.core.Flow
import com.goat.kharcha.core.Money
import com.goat.kharcha.core.Tx
import com.goat.kharcha.data.Repo
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActivityScreen() {
    val nav = LocalNav.current
    val txs by Repo.tx.collectAsState()
    val cats by Repo.categories.collectAsState()
    val settings by Repo.settings.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    val focus = LocalFocusManager.current
    val today = rememberToday()
    val zone = ZoneId.systemDefault()

    val filtered = remember(txs, query, filter) {
        val q = query.trim().lowercase()
        txs.filter { t ->
            val passFilter = when (filter) {
                "all" -> true
                "tag" -> t.needsTag && t.flow == Flow.OUT
                "in" -> t.flow == Flow.IN
                "owed" -> t.owed
                else -> t.categoryId == filter
            }
            passFilter && (q.isEmpty() || "${t.payee} ${t.note} ${t.source} ${t.owedBy}".lowercase().contains(q) ||
                Money.format(t.amount, false).replace(",", "").contains(q.replace(",", "")))
        }
    }
    val groups = remember(filtered) {
        filtered.groupBy { Cycle.date(it.time, zone) }.toList().sortedByDescending { it.first }
    }
    val range = Cycle.of(today, settings.cycleDay)
    val cycleOut = remember(filtered, range) { filtered.filter { it.flow == Flow.OUT && Cycle.date(it.time, zone) in range }.sumOf { it.amount } }
    val cycleIn = remember(filtered, range) { filtered.filter { it.flow == Flow.IN && Cycle.date(it.time, zone) in range }.sumOf { it.amount } }

    val chips = remember(cats, txs) {
        buildList {
            add("all" to "All")
            if (txs.any { it.needsTag && it.flow == Flow.OUT }) add("tag" to "Needs a reason")
            if (txs.any { it.flow == Flow.IN }) add("in" to "Money in")
            if (txs.any { it.owed }) add("owed" to "Owed to you")
            cats.filter { !it.income }.forEach { add(it.id to it.name) }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), state = rememberLazyListState(), contentPadding = bottomInset()) {
        item(key = "head") {
            ScreenHeader("Activity", "${range.label()}: ${Money.round(cycleOut)} out" + if (cycleIn > 0) " · ${Money.round(cycleIn)} in" else "")
            Field(
                value = query,
                onChange = { query = it },
                placeholder = "Search payee, note or amount",
                leading = { Glyph("search", P.muted, 18.dp) },
                trailing = if (query.isNotEmpty()) ({ IconButton("close", size = 26.dp, bg = P.surface3) { query = ""; focus.clearFocus() } }) else null,
                keyboard = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }
        item(key = "chips") {
            LazyRow(Modifier.padding(top = 12.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(chips, key = { it.first }) { (k, label) ->
                    val c = cats.firstOrNull { it.id == k }
                    Chip(label, selected = filter == k, dot = c?.let { catColor(it.color, P.dark) }) { filter = k }
                }
            }
        }
        if (groups.isEmpty()) {
            item(key = "none") {
                Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Glyph(if (txs.isEmpty()) "wallet" else "search", P.faint, 32.dp)
                    Text(
                        if (txs.isEmpty()) "No payments yet" else "Nothing matches",
                        style = T.title, color = P.fg, modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        if (txs.isEmpty()) "Payments appear here as they happen, or add one with the + button."
                        else "Try another word, or clear the filter.",
                        style = T.small, color = P.muted, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        groups.forEach { (day, list) ->
            item(key = "d-$day") {
                DayHeader(day, today, list)
            }
            items(list, key = { "t-${it.id}" }) { t ->
                Box(Modifier.animateItemPlacement()) { TxRow(t, cats) { nav.edit(t) } }
            }
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate, today: LocalDate, list: List<Tx>) {
    val out = list.filter { it.flow == Flow.OUT }.sumOf { it.amount }
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Eyebrow(Cycle.dayLabel(day, today), modifier = Modifier.weight(1f))
        if (out > 0) Text(Money.round(out), style = T.numSmall, color = P.muted)
    }
}
