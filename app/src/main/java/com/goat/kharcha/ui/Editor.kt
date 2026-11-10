package com.goat.kharcha.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goat.kharcha.core.AskMode
import com.goat.kharcha.core.Category
import com.goat.kharcha.core.Cycle
import com.goat.kharcha.core.Flow
import com.goat.kharcha.core.Linker
import com.goat.kharcha.core.Money
import com.goat.kharcha.core.Mood
import com.goat.kharcha.core.Suggest
import com.goat.kharcha.core.Tx
import com.goat.kharcha.core.merchantKey
import com.goat.kharcha.data.Repo
import com.goat.kharcha.service.Prompts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class EditorMode { ASK, EDIT, NEW }

/** The one form for tagging, editing and adding a payment. Used in the app and over other apps. */
@Composable
fun TagEditor(
    initial: Tx,
    mode: EditorMode,
    onSaved: (Tx) -> Unit,
    onDeleted: ((Tx) -> Unit)?,
    onClose: () -> Unit,
    showClose: Boolean = true,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val cats by Repo.categories.collectAsState()
    val rules by Repo.rules.collectAsState()
    val settings by Repo.settings.collectAsState()
    val zone = ZoneId.systemDefault()

    var amountText by remember { mutableStateOf(Money.toInput(initial.amount)) }
    var merchant by remember { mutableStateOf(initial.merchant.orEmpty()) }
    var flow by remember { mutableStateOf(initial.flow) }
    var categoryId by remember {
        mutableStateOf(initial.categoryId ?: if (initial.flow == Flow.OUT) rules[merchantKey(initial.merchant)] else null)
    }
    var mood by remember { mutableStateOf(initial.mood) }
    var note by remember { mutableStateOf(initial.note) }
    var owed by remember { mutableStateOf(initial.owed) }
    var owedBy by remember { mutableStateOf(initial.owedBy) }
    var rememberPlace by remember { mutableStateOf(true) }
    var date by remember { mutableStateOf(Instant.ofEpochMilli(initial.time).atZone(zone).toLocalDate()) }
    var saving by remember { mutableStateOf(false) }

    val amount = Money.parseInput(amountText)
    val visibleCats = cats.filter { it.income == (flow == Flow.IN) }.sortedBy { it.order }
    val chosen = categoryId?.takeIf { id -> visibleCats.any { it.id == id } }
    val canSave = amount != null && chosen != null && !saving

    val heading = when (mode) {
        EditorMode.ASK -> if (initial.flow == Flow.IN) "Money came in. Where from?" else "Money just left. What was it for?"
        EditorMode.EDIT -> if (initial.flow == Flow.IN) "Money in" else "Payment"
        EditorMode.NEW -> "New expense"
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        Eyebrow(heading, color = if (mode == EditorMode.ASK) P.warn else P.muted, modifier = Modifier.weight(1f))
        if (showClose) IconButton("close", size = 34.dp, onClick = onClose, contentDescription = "Close")
    }

    // What the notification said, so the person remembers which payment this was.
    val quote = initial.raw.split(Linker.SEP).map { it.trim() }.filter { it.isNotEmpty() }
    if (quote.isNotEmpty() && mode != EditorMode.NEW) {
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp))
                .background(P.surface2).padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Text(
                listOf(initial.source.ifBlank { "Notification" }, dateOf(initial.time) + ", " + timeOf(initial.time)).joinToString(" · "),
                style = T.small.copy(fontWeight = FontWeight.Bold), color = P.fg,
            )
            quote.take(3).forEach { q ->
                Text("“$q”", style = T.small, color = P.muted, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            }
            if (quote.size > 1) {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph("link", P.accent, 14.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Seen in ${quote.size} apps, counted once", style = T.small, color = P.accent)
                }
            }
        }
    }

    if (mode != EditorMode.ASK) {
        Spacer(Modifier.height(14.dp))
        Segmented(
            options = listOf(Flow.OUT to "Money out", Flow.IN to "Money in"),
            selected = flow,
            colorFor = { if (it == Flow.IN) P.good else null },
            onSelect = { flow = it; categoryId = null },
        )
    }

    // Amount
    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.Bottom) {
        Text("Rs", style = T.h2.copy(fontWeight = FontWeight.SemiBold), color = P.muted, modifier = Modifier.padding(bottom = 8.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = amountText,
            onValueChange = { v ->
                val clean = v.filter { it.isDigit() || it == '.' }
                if (clean.count { it == '.' } <= 1 && clean.substringAfter('.', "").length <= 2 && clean.substringBefore('.').length <= 9) amountText = clean
            },
            textStyle = T.amountXl.copy(color = P.fg),
            singleLine = true,
            cursorBrush = SolidColor(P.accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (amountText.isEmpty()) Text("0", style = T.amountXl, color = P.faint)
                    inner()
                }
            },
        )
    }
    val hours = amount?.let { Money.hours(it, settings.hourlyRate) }
    if (hours != null && flow == Flow.OUT) Text("≈ $hours", style = T.small.copy(fontWeight = FontWeight.Bold), color = P.accent)

    Spacer(Modifier.height(14.dp))
    Field(
        value = merchant,
        onChange = { merchant = it.take(40) },
        label = if (flow == Flow.IN) "From" else "Where",
        placeholder = if (flow == Flow.IN) "Client, person or app" else "Shop, app or person",
        keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
    )

    // What the app already knows about this place.
    val key = merchantKey(merchant)
    val ruleCat = if (flow == Flow.OUT && key.length >= 2) rules[key]?.let { id -> cats.firstOrNull { it.id == id } } else null
    val txs by Repo.tx.collectAsState()
    val hint: String? = when {
        flow == Flow.IN -> null
        ruleCat != null -> {
            val n = txs.count { merchantKey(it.merchant) == key && it.categoryId == ruleCat.id && it.id != initial.id }
            if (n > 0) "${merchant.trim()} is usually ${ruleCat.name} · filed there ${n}×" else "${merchant.trim()} files under ${ruleCat.name}"
        }
        key.length >= 2 -> "New place. Pick once and ${merchant.trim()} files itself next time."
        else -> null
    }
    if (hint != null) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph(if (ruleCat != null) "sparkle" else "bulb", if (ruleCat != null) P.accent else P.muted, 15.dp)
            Spacer(Modifier.width(7.dp))
            Text(hint, style = T.small, color = if (ruleCat != null) P.accent else P.muted, maxLines = 2)
        }
    }

    // Likely picks for a payment the app hasn't seen before.
    if (mode == EditorMode.ASK && flow == Flow.OUT && ruleCat == null) {
        val hour = Instant.ofEpochMilli(initial.time).atZone(zone).hour
        val ranked = remember(initial.id) { Suggest.rank(initial.merchant, "${initial.source} ${initial.raw}", hour, rules, txs, cats).take(3) }
        if (ranked.isNotEmpty()) {
            Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph("sparkle", P.accent, 15.dp)
                ranked.forEach { id ->
                    val c = cats.firstOrNull { it.id == id } ?: return@forEach
                    Chip(c.name, selected = chosen == id, dot = catColor(c.color, P.dark)) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        categoryId = id
                    }
                }
            }
        }
    }

    Text("Category", style = T.small.copy(fontWeight = FontWeight.Bold), color = P.muted, modifier = Modifier.padding(top = 18.dp, bottom = 9.dp))
    CategoryGrid(visibleCats, chosen) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        categoryId = it
    }

    if (flow == Flow.OUT) {
        Text("Was it worth it?", style = T.small.copy(fontWeight = FontWeight.Bold), color = P.muted, modifier = Modifier.padding(top = 18.dp, bottom = 9.dp))
        Segmented(
            options = listOf(Mood.WORTH to "Worth it", Mood.MEH to "Meh", Mood.REGRET to "Regret"),
            selected = mood,
            colorFor = { when (it) { Mood.WORTH -> P.good; Mood.REGRET -> P.bad; else -> null } },
            onSelect = { mood = if (mood == it) Mood.NONE else it },
        )
    }

    if (mode != EditorMode.ASK) {
        Text("Day", style = T.small.copy(fontWeight = FontWeight.Bold), color = P.muted, modifier = Modifier.padding(top = 18.dp, bottom = 9.dp))
        val today = remember { LocalDate.now() }
        val df = remember { DateTimeFormatter.ofPattern("EEE d", Locale.US) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val days = (0..20).map { today.minusDays(it.toLong()) }.let { if (date !in it) listOf(date) + it else it }
            days.forEach { d ->
                val label = when (d) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> d.format(df) }
                Chip(label, selected = d == date) { date = d }
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Field(value = note, onChange = { note = it.take(120) }, label = "Note", placeholder = "Optional")

    if (flow == Flow.OUT) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Someone owes me for this", style = T.title, color = P.fg)
                Text("Stays on your list until they pay you back", style = T.small, color = P.muted)
            }
            KSwitch(owed) { owed = it }
        }
        AnimatedVisibility(owed, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Field(value = owedBy, onChange = { owedBy = it.take(30) }, placeholder = "Who owes you", modifier = Modifier.padding(top = 10.dp),
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        }
        if (key.length >= 2 && chosen != null && rules[key] != chosen) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Always file ${merchant.trim()} here", style = T.title, color = P.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (settings.askMode == AskMode.NEW_PLACES) "Next payment there files itself without asking" else "Shown first next time",
                        style = T.small, color = P.muted,
                    )
                }
                KSwitch(rememberPlace) { rememberPlace = it }
            }
        }
    }

    Row(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (onDeleted != null && mode != EditorMode.NEW) {
            ConfirmButton("Delete", "Tap to delete", Modifier.weight(0.42f)) {
                scope.launch {
                    val removed = Repo.delete(initial.id)
                    Prompts.cancel(ctx, initial.id)
                    if (removed != null) onDeleted(removed)
                }
            }
        }
        PrimaryButton(if (chosen == null) "Pick a category" else "Save", Modifier.weight(0.58f), enabled = canSave) {
            val a = amount ?: return@PrimaryButton
            val c = chosen ?: return@PrimaryButton
            saving = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            val origDate = Instant.ofEpochMilli(initial.time).atZone(zone)
            val time = if (date == origDate.toLocalDate()) initial.time
            else date.atTime(origDate.toLocalTime()).atZone(zone).toInstant().toEpochMilli().coerceAtMost(System.currentTimeMillis())
            val updated = initial.copy(
                amount = a,
                flow = flow,
                merchant = merchant.trim().ifBlank { null },
                categoryId = c,
                mood = if (flow == Flow.OUT) mood else Mood.NONE,
                note = note.trim(),
                owed = owed && flow == Flow.OUT,
                owedBy = if (owed) owedBy.trim() else "",
                time = time,
            )
            scope.launch {
                val saved = Repo.save(updated, remember = rememberPlace && flow == Flow.OUT)
                Prompts.cancel(ctx, saved.id)
                onSaved(saved)
            }
        }
    }
}

@Composable
fun CategoryGrid(cats: List<Category>, selected: String?, columns: Int = 4, big: Boolean = false, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cats.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c ->
                    val on = c.id == selected
                    val color = catColor(c.color, P.dark)
                    val border by animateColorAsState(if (on) color else Color.Transparent, label = "catBorder")
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .press(role = androidx.compose.ui.semantics.Role.RadioButton) { onPick(c.id) }
                            .background(if (on) color.copy(alpha = 0.16f) else P.surface2)
                            .border(1.5.dp, border, RoundedCornerShape(16.dp))
                            .padding(top = if (big) 14.dp else 11.dp, bottom = if (big) 12.dp else 9.dp, start = 2.dp, end = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CatIcon(c, size = if (big) 40.dp else 34.dp, iconSize = if (big) 21.dp else 19.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(c.name, style = T.chip.copy(fontSize = T.chip.fontSize * 0.88f), color = if (on) P.fg else P.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The quick sheet shown over other apps when a payment notification is tapped. */
@Composable
fun QuickTag(txId: Long, onDone: () -> Unit) {
    val settings by Repo.settings.collectAsState()
    KharchaTheme(isDarkFor(settings.theme)) {
        val loaded by Repo.loaded.collectAsState()
        val txs by Repo.tx.collectAsState()
        var visible by remember { mutableStateOf(false) }
        var closing by remember { mutableStateOf(false) }
        val toaster = remember { Toaster() }
        LaunchedEffect(Unit) { visible = true }
        LaunchedEffect(closing) {
            if (closing) { visible = false; delay(260); onDone() }
        }
        val tx = remember(txId, loaded) { txs.firstOrNull { it.id == txId } }
        Box(Modifier.fillMaxSize()) {
            Sheet(visible = visible && loaded, onDismiss = { closing = true }) {
                if (tx == null) {
                    Spacer(Modifier.height(8.dp))
                    Text("This payment is gone", style = T.h2, color = P.fg)
                    Text("It was deleted or merged with another one.", style = T.body, color = P.muted, modifier = Modifier.padding(top = 6.dp))
                    PrimaryButton("Close", Modifier.fillMaxWidth().padding(top = 18.dp)) { closing = true }
                } else {
                    androidx.compose.runtime.key(tx.id) {
                        TagEditor(
                            initial = tx,
                            mode = if (tx.needsTag) EditorMode.ASK else EditorMode.EDIT,
                            onSaved = { closing = true },
                            onDeleted = { closing = true },
                            onClose = { closing = true },
                        )
                    }
                }
            }
            ToastHost(toaster, 24.dp)
        }
    }
}

fun daysAgoLabel(d: LocalDate, today: LocalDate): String = Cycle.dayLabel(d, today)
