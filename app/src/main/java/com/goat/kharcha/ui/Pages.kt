package com.goat.kharcha.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.goat.kharcha.MainActivity
import com.goat.kharcha.core.Flow
import com.goat.kharcha.core.Linker
import com.goat.kharcha.core.Money
import com.goat.kharcha.core.Tx
import com.goat.kharcha.data.Repo
import com.goat.kharcha.service.Prompts
import kotlinx.coroutines.launch

/** The app mark: a coin ring with a K, drawn so it stays sharp at any size. */
@Composable
fun Logo(size: Dp = 64.dp, ring: Color = P.accent, letter: Color = P.fg, dot: Color = P.good) {
    Canvas(Modifier.size(size)) {
        val u = this.size.minDimension / 108f
        fun o(x: Float, y: Float) = Offset(x * u, y * u)
        drawCircle(ring, radius = 22f * u * 1.55f, center = o(54f, 54f), style = Stroke(width = 4.5f * u * 1.55f))
        val w = 5f * u * 1.55f
        val c = o(54f, 54f)
        fun s(x: Float, y: Float) = Offset(c.x + (x - 54f) * u * 1.55f, c.y + (y - 54f) * u * 1.55f)
        drawLine(letter, s(46f, 42f), s(46f, 66f), w, StrokeCap.Round)
        drawLine(letter, s(46f, 55f), s(60f, 42f), w, StrokeCap.Round)
        drawLine(letter, s(51f, 51f), s(61f, 66f), w, StrokeCap.Round)
        drawCircle(dot, radius = 4.5f * u * 1.55f, center = s(72f, 34f))
    }
}

@Composable
fun LockScreen(activity: MainActivity) {
    LaunchedEffect(Unit) { activity.authenticate() }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Logo(84.dp)
        Text("Kharcha is locked", style = T.h1, color = P.fg, modifier = Modifier.padding(top = 22.dp))
        Text("Unlock to see your money", style = T.body, color = P.muted, modifier = Modifier.padding(top = 6.dp))
        PrimaryButton("Unlock", Modifier.padding(top = 28.dp).widthIn(min = 200.dp)) { activity.authenticate() }
    }
}

// ---------- tag queue ----------

@Composable
fun QueueScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    val haptic = LocalHapticFeedback.current
    val txs by Repo.tx.collectAsState()
    val cats by Repo.categories.collectAsState()
    val skipped = remember { mutableStateListOf<Long>() }
    val handled = remember { mutableStateListOf<Long>() }
    var done by remember { mutableIntStateOf(0) }
    val pending = remember(txs, skipped.toList()) {
        val all = txs.filter { it.needsTag && it.flow == Flow.OUT }.sortedByDescending { it.time }
        all.filter { it.id !in skipped } + all.filter { it.id in skipped }
    }
    val current = pending.firstOrNull()

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp)) {
        ScreenHeader(
            "Tag payments",
            if (pending.isNotEmpty()) ("${pending.size} to go" + (if (done > 0) " · $done done" else "")) else null,
            onBack = nav.back,
        )
        AnimatedContent(
            targetState = current,
            contentKey = { it?.id },
            transitionSpec = {
                (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it / 2 } + fadeIn()) togetherWith
                    (slideOutHorizontally(tween(220)) { -it / 2 } + fadeOut(tween(180)))
            },
            label = "queue",
            modifier = Modifier.weight(1f),
        ) { t ->
            if (t == null) {
                CaughtUp(done) { nav.back() }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                    Card(Modifier.fillMaxWidth(), padding = PaddingValues(20.dp)) {
                        Eyebrow(listOf(t.source.ifBlank { "Payment" }, dateOf(t.time), timeOf(t.time)).joinToString(" · "))
                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Rs", style = T.h2.copy(fontWeight = FontWeight.SemiBold), color = P.muted, modifier = Modifier.padding(bottom = 8.dp, end = 8.dp))
                            Text(Money.format(t.amount, false), style = T.amountXl, color = P.fg)
                        }
                        Text(t.payee, style = T.h2, color = if (t.merchant == null) P.muted else P.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val q = t.raw.split(Linker.SEP).firstOrNull()?.trim().orEmpty()
                        if (q.isNotEmpty()) Text("“$q”", style = T.small, color = P.muted, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                    }
                    Text("What was it for?", style = T.title, color = P.fg, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
                    CategoryGrid(cats.filter { !it.income }.sortedBy { it.order }, null, columns = 4, big = true) { id ->
                        if (t.id in handled) return@CategoryGrid
                        handled.add(t.id)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        done++
                        Repo.scope.launch {
                            Repo.tag(t.id, id, remember = true)
                            Prompts.cancel(ctx, t.id)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("Skip", Modifier.weight(1f), icon = "skip") {
                            if (pending.size > 1) skipped.add(t.id) else toaster.show("This is the last one")
                        }
                        SecondaryButton("Details", Modifier.weight(1f), icon = "edit") { nav.edit(t) }
                    }
                    Text(
                        "Not a payment? Delete it",
                        style = T.chip, color = P.bad, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(10.dp)).press {
                            Repo.scope.launch {
                                val removed = Repo.delete(t.id)
                                Prompts.cancel(ctx, t.id)
                                if (removed != null) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        toaster.show("Deleted", "Undo") { Repo.scope.launch { Repo.restore(removed) } }
                                    }
                                }
                            }
                        }.padding(vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CaughtUp(done: Int, onDone: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val s by animateFloatAsState(if (shown) 1f else 0.6f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow), label = "pop")
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(
            Modifier.size(88.dp * s).clip(CircleShape).background(P.good.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Glyph("check", P.good, 40.dp * s) }
        Text("All caught up", style = T.h1, color = P.fg, modifier = Modifier.padding(top = 20.dp))
        Text(
            if (done > 0) "You tagged $done payment${if (done == 1) "" else "s"}. Those places will file themselves from now on." else "Every payment has a reason.",
            style = T.body, color = P.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, start = 12.dp, end = 12.dp),
        )
        PrimaryButton("Done", Modifier.padding(top = 26.dp).widthIn(min = 180.dp), onClick = onDone)
    }
}

// ---------- add by hand ----------

@Composable
fun AddScreen() {
    val nav = LocalNav.current
    val toaster = LocalToaster.current
    val haptic = LocalHapticFeedback.current
    val settings by Repo.settings.collectAsState()
    var step by remember { mutableIntStateOf(0) }
    var amountText by remember { mutableStateOf("") }
    val amount = Money.parseInput(amountText)

    AnimatedContent(step, transitionSpec = {
        if (targetState > initialState) (slideInHorizontally { it / 3 } + fadeIn()) togetherWith fadeOut()
        else fadeIn() togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
    }, label = "add") { st ->
        if (st == 0) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp)) {
                ScreenHeader("Add a payment", "For cash and anything the app didn't catch", onBack = nav.back)
                Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("Rs", style = T.h2.copy(fontWeight = FontWeight.SemiBold), color = P.muted, modifier = Modifier.padding(bottom = 10.dp, end = 8.dp))
                        Text(
                            if (amountText.isEmpty()) "0" else formatTyped(amountText),
                            style = T.hero, color = if (amountText.isEmpty()) P.faint else P.fg, maxLines = 1,
                        )
                    }
                    val hours = amount?.let { Money.hours(it, settings.hourlyRate) }
                    Text(hours?.let { "≈ $it" } ?: " ", style = T.small.copy(fontWeight = FontWeight.Bold), color = P.accent, modifier = Modifier.padding(top = 6.dp))
                    Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(500, 1000, 2000, 5000).forEach { v ->
                            Chip(Money.group(v.toLong()), selected = false) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                amountText = v.toString()
                            }
                        }
                    }
                }
                Keypad { key ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    amountText = when (key) {
                        "⌫" -> amountText.dropLast(1)
                        "." -> if ('.' in amountText) amountText else if (amountText.isEmpty()) "0." else "$amountText."
                        else -> {
                            val next = amountText + key
                            val ok = next.substringBefore('.').length <= 9 && next.substringAfter('.', "").length <= 2
                            if (!ok) amountText else if (amountText == "0") key else next
                        }
                    }
                }
                PrimaryButton("Continue", Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp), enabled = amount != null) { step = 1 }
            }
        } else {
            val initial = remember(amount) {
                Tx(amount = amount ?: 0, merchant = null, time = System.currentTimeMillis(), manual = true)
            }
            Column(
                Modifier.fillMaxSize().statusBarsPadding().imePadding().navigationBarsPadding()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                TagEditor(
                    initial = initial,
                    mode = EditorMode.NEW,
                    onSaved = { saved -> nav.back(); toaster.show(savedMessage(saved)) },
                    onDeleted = null,
                    onClose = { step = 0 },
                )
            }
        }
    }
}

private fun formatTyped(s: String): String {
    val whole = s.substringBefore('.')
    val g = whole.toLongOrNull()?.let { Money.group(it) } ?: whole
    return if ('.' in s) g + "." + s.substringAfter('.') else g
}

@Composable
private fun Keypad(onKey: (String) -> Unit) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "⌫")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier.weight(1f).heightIn(min = 58.dp).clip(RoundedCornerShape(18.dp)).press { onKey(k) }
                            .background(P.surface).border(1.dp, P.line, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (k == "⌫") Glyph("back", P.fg, 22.dp, contentDescription = "Delete")
                        else Text(k, style = T.h2.copy(fontFamily = Display), color = P.fg)
                    }
                }
            }
        }
    }
}
