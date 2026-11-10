package com.goat.kharcha.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.goat.kharcha.MainActivity
import com.goat.kharcha.core.Money
import com.goat.kharcha.data.Repo
import com.goat.kharcha.service.Prompts
import com.goat.kharcha.service.RecapWorker
import kotlinx.coroutines.launch

@Composable
fun Onboarding(activity: MainActivity) {
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(Repo.settings.value.name) }
    var budget by remember { mutableStateOf("") }
    var cycleDay by remember { mutableIntStateOf(1) }
    var rate by remember { mutableStateOf("") }

    BackHandler(enabled = step > 0) { step-- }

    fun finish(withSample: Boolean) {
        Repo.updateSettings {
            it.copy(
                name = name.trim(),
                budget = Money.parseInput(budget) ?: 0,
                cycleDay = cycleDay,
                hourlyRate = Money.parseInput(rate) ?: 0,
                onboarded = true,
            )
        }
        RecapWorker.schedule(activity, Repo.settings.value.recap)
        if (withSample) Repo.scope.launch { Repo.loadSample() }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 22.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (i <= step) P.accent else P.surface2))
            }
        }
        AnimatedContent(step, transitionSpec = {
            if (targetState > initialState) (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
            else (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
        }, label = "onboard", modifier = Modifier.weight(1f)) { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 28.dp, bottom = 16.dp)) {
                when (s) {
                    0 -> Welcome(name) { name = it.take(24) }
                    1 -> BudgetStep(budget, { budget = it }, cycleDay, { cycleDay = it }, rate, { rate = it })
                    else -> PermissionStep(activity)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 0) SecondaryButton("Back", Modifier.weight(0.35f)) { step-- }
            when (step) {
                0 -> PrimaryButton("Get started", Modifier.weight(1f)) { step = 1 }
                1 -> PrimaryButton(if (budget.isBlank()) "Skip for now" else "Continue", Modifier.weight(0.65f)) { step = 2 }
                else -> PrimaryButton("Start tracking", Modifier.weight(0.65f)) { finish(false) }
            }
        }
        if (step == 2) {
            Text(
                "Or explore with sample payments first",
                style = T.chip, color = P.accent,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 14.dp).clip(RoundedCornerShape(8.dp))
                    .press { finish(true) }.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun Welcome(name: String, onName: (String) -> Unit) {
    Logo(72.dp)
    Text("Kharcha", style = T.hero.copy(fontSize = T.hero.fontSize * 0.92f), color = P.fg, modifier = Modifier.padding(top = 18.dp))
    Text("Every rupee, explained.", style = T.h2.copy(fontWeight = FontWeight.SemiBold), color = P.muted)
    Spacer(Modifier.height(26.dp))
    Feature("bell", "Catches every payment", "Bank texts, bank and wallet apps, and food, ride and shopping apps.")
    Feature("tag", "Asks once per place", "Tag Foodpanda as Food one time. Every order after files itself.")
    Feature("wallet", "Tells you what's safe today", "Your budget, minus what's spent and the bills still coming, split across the days left.")
    Feature("shield", "Stays on your phone", "No account, no bank login, no uploads.")
    Spacer(Modifier.height(18.dp))
    Field(name, onName, label = "What should Kharcha call you?", placeholder = "Your name",
        keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done))
}

@Composable
private fun Feature(icon: String, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(P.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Glyph(icon, P.accent, 19.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = T.title, color = P.fg)
            Text(body, style = T.small, color = P.muted)
        }
    }
}

@Composable
private fun BudgetStep(budget: String, onBudget: (String) -> Unit, cycleDay: Int, onCycle: (Int) -> Unit, rate: String, onRate: (String) -> Unit) {
    Text("How much do you want to spend a month?", style = T.h1, color = P.fg)
    Text("Everything except savings. You can change it anytime.", style = T.body, color = P.muted, modifier = Modifier.padding(top = 6.dp))
    Row(Modifier.padding(top = 24.dp), verticalAlignment = Alignment.Bottom) {
        Text("Rs", style = T.h2.copy(fontWeight = FontWeight.SemiBold), color = P.muted, modifier = Modifier.padding(bottom = 10.dp, end = 10.dp))
        BasicTextField(
            value = budget,
            onValueChange = { v -> onBudget(v.filter { it.isDigit() }.take(9)) },
            textStyle = T.hero.copy(color = P.fg),
            singleLine = true,
            cursorBrush = SolidColor(P.accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (budget.isEmpty()) Text("0", style = T.hero, color = P.faint)
                    inner()
                }
            },
        )
    }
    val b = Money.parseInput(budget)
    AnimatedVisibility(b != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Text(
            "About ${Money.round((b ?: 0) / 30)} a day",
            style = T.small.copy(fontWeight = FontWeight.Bold), color = P.accent, modifier = Modifier.padding(top = 4.dp),
        )
    }
    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(50_000, 80_000, 120_000).forEach { v -> Chip(Money.round(v * 100L), selected = budget == v.toString()) { onBudget(v.toString()) } }
    }

    Card(Modifier.fillMaxWidth().padding(top = 26.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Month starts on day", style = T.title, color = P.fg)
                Text("Use your salary or payout day", style = T.small, color = P.muted)
            }
            Stepper(cycleDay, 1..28, onCycle)
        }
    }
    Spacer(Modifier.height(14.dp))
    Field(
        rate, { v -> onRate(v.filter { it.isDigit() }.take(7)) },
        label = "What does an hour of your work earn? (optional)",
        placeholder = "e.g. 2000",
        leading = { Text("Rs", style = T.title, color = P.muted) },
        keyboard = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
    )
    Text("Kharcha will show prices as hours of your work. A Rs 4,500 order hits different as 2.3 hours.", style = T.small, color = P.muted, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun PermissionStep(activity: MainActivity) {
    val ctx = LocalContext.current
    val listening = listenerEnabled()
    var canPost by remember { mutableStateOf(Prompts.canPost(ctx)) }
    val tick = resumeTick()
    androidx.compose.runtime.LaunchedEffect(tick) { canPost = Prompts.canPost(ctx) }
    var showHelp by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        canPost = Prompts.canPost(ctx)
        if (!ok) Permissions.openNotificationSettings(ctx)
    }

    Text("Two switches and you're set", style = T.h1, color = P.fg)
    Text("Kharcha only reads notifications that mention money. Everything stays on this phone.", style = T.body, color = P.muted, modifier = Modifier.padding(top = 6.dp))
    Spacer(Modifier.height(22.dp))

    PermissionCard(
        n = 1, done = listening,
        title = "Read payment notifications",
        body = "Find Kharcha in the list and switch it on. This is how payments are caught from any app.",
        action = "Open settings",
    ) {
        activity.markExternalTrip()
        Permissions.openListenerSettings(ctx)
    }
    if (!listening) {
        Text(
            if (showHelp) "Hide help" else "Android says \"Restricted setting\"?",
            style = T.chip, color = P.accent,
            modifier = Modifier.padding(top = 10.dp, start = 4.dp).clip(RoundedCornerShape(8.dp)).press { showHelp = !showHelp }.padding(4.dp),
        )
        AnimatedVisibility(showHelp, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Card(Modifier.fillMaxWidth().padding(top = 8.dp), color = P.surface2) {
                Text("Android blocks this switch for apps installed outside the Play Store until you allow it once:", style = T.small, color = P.fg)
                listOf(
                    "Open App info below",
                    "Tap the ⋮ menu in the top corner",
                    "Tap \"Allow restricted settings\" and confirm",
                    "Come back and tap Open settings again",
                ).forEachIndexed { i, s ->
                    Text("${i + 1}. $s", style = T.small, color = P.muted, modifier = Modifier.padding(top = 6.dp))
                }
                SecondaryButton("Open App info", Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    activity.markExternalTrip()
                    Permissions.openAppInfo(ctx)
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    PermissionCard(
        n = 2, done = canPost,
        title = "Ask me after each payment",
        body = "A notification with one-tap categories, right when money leaves.",
        action = "Allow",
    ) {
        activity.markExternalTrip()
        if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else Permissions.openNotificationSettings(ctx)
    }
}

@Composable
private fun PermissionCard(n: Int, done: Boolean, title: String, body: String, action: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(P.surface)
            .border(1.dp, if (done) P.good.copy(alpha = 0.5f) else P.line, shape).padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(if (done) P.good.copy(alpha = 0.18f) else P.surface2),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Glyph("check", P.good, 17.dp) else Text("$n", style = T.title, color = P.fg)
            }
            Spacer(Modifier.width(12.dp))
            Text(title, style = T.title, color = P.fg, modifier = Modifier.weight(1f))
            if (done) Text("Done", style = T.chip, color = P.good)
        }
        Text(body, style = T.small, color = P.muted, modifier = Modifier.padding(top = 8.dp))
        if (!done) PrimaryButton(action, Modifier.fillMaxWidth().padding(top = 14.dp), onClick = onClick)
    }
}
