package com.goat.kharcha.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goat.kharcha.MainActivity
import com.goat.kharcha.core.Category
import com.goat.kharcha.core.Cycle
import com.goat.kharcha.core.Flow
import com.goat.kharcha.core.Money
import com.goat.kharcha.core.Mood
import com.goat.kharcha.core.Tx
import com.goat.kharcha.data.Repo
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Tab(val label: String, val icon: String) {
    HOME("Home", "home"), ACTIVITY("Activity", "list"), INSIGHTS("Insights", "chart"), SETTINGS("Settings", "sliders")
}

enum class Page { ADD, QUEUE, CATEGORIES, PLACES, SOURCES, LAB }

/** Navigation for every screen: switch tabs, push pages, open a payment for editing. */
class Nav(
    val tab: (Tab) -> Unit,
    val push: (Page) -> Unit,
    val back: () -> Unit,
    val edit: (Tx) -> Unit,
    val activity: MainActivity?,
)

val LocalNav = staticCompositionLocalOf<Nav> { error("Nav not provided") }

@Composable
fun KharchaRoot(activity: MainActivity) {
    val settings by Repo.settings.collectAsState()
    val dark = isDarkFor(settings.theme)
    SideEffect { activity.setBars(dark) }
    KharchaTheme(dark) {
        val loaded by Repo.loaded.collectAsState()
        val locked by activity.locked
        Box(Modifier.fillMaxSize().background(P.bg)) {
            if (loaded) {
                val stage = when {
                    locked -> 0
                    !settings.onboarded -> 1
                    else -> 2
                }
                AnimatedContent(stage, transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(200)) }, label = "stage") { s ->
                    when (s) {
                        0 -> LockScreen(activity)
                        1 -> Onboarding(activity)
                        else -> MainShell(activity)
                    }
                }
            }
        }
    }
}

@Composable
private fun MainShell(activity: MainActivity) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    val stack = remember { mutableStateListOf<Page>() }
    var editing by remember { mutableStateOf<Tx?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    val toaster = remember { Toaster() }
    val holder = rememberSaveableStateHolder()

    val nav = remember {
        Nav(
            tab = { t -> stack.clear(); tab = t },
            push = { p -> if (stack.lastOrNull() != p) stack.add(p) },
            back = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) },
            edit = { t -> editing = t; sheetOpen = true },
            activity = activity,
        )
    }

    val request by activity.openRequest
    LaunchedEffect(request) {
        if (request == MainActivity.OPEN_QUEUE) {
            sheetOpen = false
            if (stack.lastOrNull() != Page.QUEUE) { stack.clear(); stack.add(Page.QUEUE) }
        }
        if (request != null) activity.openRequest.value = null
    }

    BackHandler(enabled = !sheetOpen && (stack.isNotEmpty() || tab != Tab.HOME)) {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) else tab = Tab.HOME
    }

    CompositionLocalProvider(LocalNav provides nav, LocalToaster provides toaster) {
        Box(Modifier.fillMaxSize().background(P.bg)) {
            val top = stack.lastOrNull()
            AnimatedContent(
                targetState = top to stack.size,
                transitionSpec = {
                    if (targetState.second > initialState.second) {
                        (slideInHorizontally(tween(300)) { it / 3 } + fadeIn(tween(240))) togetherWith fadeOut(tween(180))
                    } else {
                        fadeIn(tween(240)) togetherWith (slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(200)))
                    }
                },
                label = "page",
            ) { (page, _) ->
                if (page == null) {
                    AnimatedContent(tab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "tab") { t ->
                        holder.SaveableStateProvider("tab-${t.name}") {
                            when (t) {
                                Tab.HOME -> HomeScreen()
                                Tab.ACTIVITY -> ActivityScreen()
                                Tab.INSIGHTS -> InsightsScreen()
                                Tab.SETTINGS -> SettingsScreen()
                            }
                        }
                    }
                } else {
                    holder.SaveableStateProvider("page-${page.name}") {
                        when (page) {
                            Page.ADD -> AddScreen()
                            Page.QUEUE -> QueueScreen()
                            Page.CATEGORIES -> CategoriesScreen()
                            Page.PLACES -> PlacesScreen()
                            Page.SOURCES -> SourcesScreen()
                            Page.LAB -> LabScreen()
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = top == null,
                enter = slideInVertically(tween(260)) { it } + fadeIn(),
                exit = slideOutVertically(tween(200)) { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                BottomBar(tab, onTab = { tab = it }, onAdd = { nav.push(Page.ADD) })
            }

            Sheet(visible = sheetOpen, onDismiss = { sheetOpen = false }) {
                val t = editing
                if (t != null) {
                    key(t.id) {
                        TagEditor(
                            initial = t,
                            mode = if (t.needsTag && !t.manual) EditorMode.ASK else EditorMode.EDIT,
                            onSaved = { saved ->
                                sheetOpen = false
                                toaster.show(savedMessage(saved))
                            },
                            onDeleted = { removed ->
                                sheetOpen = false
                                toaster.show("Payment deleted", "Undo") { Repo.scope.launch { Repo.restore(removed) } }
                            },
                            onClose = { sheetOpen = false },
                        )
                    }
                }
            }

            ToastHost(toaster, bottomPadding = if (top == null) 104.dp else 24.dp)
        }
    }
}

fun savedMessage(t: Tx): String {
    val cat = Repo.category(t.categoryId)?.name
    return if (cat != null) "Saved under $cat" else "Saved"
}

@Composable
private fun BottomBar(tab: Tab, onTab: (Tab) -> Unit, onAdd: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(26.dp), ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f))
                .clip(RoundedCornerShape(26.dp))
                .background(P.surface)
                .border(1.dp, P.line, RoundedCornerShape(26.dp))
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tabs = Tab.values()
            tabs.forEachIndexed { i, t ->
                if (i == 2) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .press { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onAdd() }
                                .background(P.accent),
                            contentAlignment = Alignment.Center,
                        ) { Glyph("plus", P.onAccent, 26.dp, contentDescription = "Add expense") }
                    }
                }
                val on = t == tab
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { if (!on) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onTab(t) }
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Glyph(t.icon, if (on) P.fg else P.faint, 22.dp)
                    Spacer(Modifier.height(3.dp))
                    Text(t.label, style = T.chip.copy(fontSize = T.chip.fontSize * 0.86f), color = if (on) P.fg else P.faint, maxLines = 1)
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.size(4.dp).clip(CircleShape).background(if (on) P.accent else Color.Transparent))
                }
            }
        }
    }
}

// ---------- shared rows ----------

private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val dateFmt = DateTimeFormatter.ofPattern("d MMM", Locale.US)

fun timeOf(t: Long): String = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).format(timeFmt)
fun dateOf(t: Long): String = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).format(dateFmt)

@Composable
fun TxRow(t: Tx, cats: List<Category>, showDate: Boolean = false, onClick: () -> Unit) {
    val cat = cats.firstOrNull { it.id == t.categoryId }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 9.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CatIcon(cat)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.payee, style = T.title, color = P.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = buildList {
                add(cat?.name ?: if (t.flow == Flow.IN) "Money in" else "Needs a reason")
                if (t.manual) add("Cash") else if (t.source.isNotBlank()) add(t.source)
                add(if (showDate) dateOf(t.time) + ", " + timeOf(t.time) else timeOf(t.time))
            }.joinToString(" · ")
            Text(meta, style = T.small, color = P.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (t.needsTag && t.flow == Flow.OUT || t.owed) {
                Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (t.needsTag && t.flow == Flow.OUT) Pill("Tag it", P.warn)
                    if (t.owed) Pill("Owed by ${t.owedBy.ifBlank { "someone" }}", P.accent)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            val inflow = t.flow == Flow.IN
            Text((if (inflow) "+" else "−") + Money.format(t.amount, symbol = false), style = T.num, color = if (inflow) P.good else P.fg, maxLines = 1)
            if (t.mood == Mood.REGRET) Text("regret", style = T.numSmall, color = P.bad)
            else if (t.mood == Mood.WORTH) Text("worth it", style = T.numSmall, color = P.good)
        }
    }
}

fun greeting(name: String): String {
    val h = java.time.LocalTime.now().hour
    val g = when {
        h < 5 -> "Up late"
        h < 12 -> "Morning"
        h < 17 -> "Afternoon"
        else -> "Evening"
    }
    return if (name.isBlank()) g else "$g, ${name.trim()}"
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton("back", onClick = onBack, contentDescription = "Back")
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = if (onBack != null) T.h2 else T.h1, color = P.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = T.small, color = P.muted, maxLines = 2)
        }
        if (action != null) action()
    }
}

fun todayDate() = Cycle.date(System.currentTimeMillis())
