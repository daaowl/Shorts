package com.goat.kharcha.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.goat.kharcha.core.AskMode
import com.goat.kharcha.core.Money
import com.goat.kharcha.core.Scope
import com.goat.kharcha.core.ThemeMode
import com.goat.kharcha.data.Repo
import com.goat.kharcha.service.Prompts
import com.goat.kharcha.service.RecapWorker
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val toaster = LocalToaster.current
    val s by Repo.settings.collectAsState()
    val txs by Repo.tx.collectAsState()
    val rules by Repo.rules.collectAsState()
    val apps by Repo.apps.collectAsState()
    val listening = listenerEnabled()
    var canPost by remember { mutableStateOf(Prompts.canPost(ctx)) }
    val tick = resumeTick()
    androidx.compose.runtime.LaunchedEffect(tick) { canPost = Prompts.canPost(ctx) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        canPost = Prompts.canPost(ctx)
        if (!ok) Permissions.openNotificationSettings(ctx)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val csv = Repo.csv()
        Repo.scope.launch {
            val ok = runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) } ?: error("no stream")
            }.isSuccess
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                toaster.show(if (ok) "Exported ${txs.size} payments" else "Couldn't save the file. Try another folder.")
            }
        }
    }

    var budgetText by remember { mutableStateOf(if (s.budget > 0) Money.toInput(s.budget) else "") }
    var rateText by remember { mutableStateOf(if (s.hourlyRate > 0) Money.toInput(s.hourlyRate) else "") }
    var nameText by remember { mutableStateOf(s.name) }

    LazyColumn(Modifier.fillMaxSize(), state = rememberLazyListState(), contentPadding = bottomInset(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        item(key = "head") { ScreenHeader("Settings") }

        item(key = "money") {
            GroupLabel("You and your money")
            Card(Modifier.fillMaxWidth()) {
                Field(nameText, { nameText = it.take(24); Repo.updateSettings { st -> st.copy(name = nameText.trim()) } }, label = "Your name", placeholder = "What should Kharcha call you?",
                    keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                Spacer(Modifier.height(14.dp))
                Field(
                    budgetText,
                    { v ->
                        val clean = v.filter { it.isDigit() }.take(9)
                        budgetText = clean
                        Repo.updateSettings { st -> st.copy(budget = Money.parseInput(clean) ?: 0) }
                    },
                    label = "Monthly budget",
                    placeholder = "No budget",
                    leading = { Text("Rs", style = T.title, color = P.muted) },
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.height(14.dp))
                Text("Budget cycle starts on", style = T.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = P.muted)
                Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (s.cycleDay == 1) "The 1st of each month" else "Day ${s.cycleDay} of each month",
                        style = T.title, color = P.fg, modifier = Modifier.weight(1f),
                    )
                    Stepper(s.cycleDay, 1..28) { d -> Repo.updateSettings { it.copy(cycleDay = d) } }
                }
                Text("Set it to your salary or payout day so each cycle runs payday to payday.", style = T.small, color = P.muted, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(14.dp))
                Field(
                    rateText,
                    { v ->
                        val clean = v.filter { it.isDigit() }.take(7)
                        rateText = clean
                        Repo.updateSettings { st -> st.copy(hourlyRate = Money.parseInput(clean) ?: 0) }
                    },
                    label = "What an hour of your work earns",
                    placeholder = "Optional",
                    leading = { Text("Rs", style = T.title, color = P.muted) },
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text("Shows every expense as hours of your work.", style = T.small, color = P.muted, modifier = Modifier.padding(top = 6.dp))
            }
        }

        item(key = "detect") {
            GroupLabel("Detection")
            Group {
                SettingRow(
                    "bell", "Payment detection",
                    if (listening) "On · reading payment notifications" else "Off · tap to allow notification access",
                    tint = if (listening) P.good else P.bad,
                    onClick = { nav.activity?.markExternalTrip(); Permissions.openListenerSettings(ctx) },
                )
                if (!canPost) {
                    Divider()
                    SettingRow("sparkle", "Allow payment prompts", "Needed to ask you about each payment", tint = P.warn, onClick = {
                        nav.activity?.markExternalTrip()
                        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else Permissions.openNotificationSettings(ctx)
                    })
                }
                Divider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text("Ask me about", style = T.title, color = P.fg)
                    Text(
                        if (s.askMode == AskMode.NEW_PLACES) "Places you've tagged before are filed for you" else "Every payment asks, with your usual pick first",
                        style = T.small, color = P.muted, modifier = Modifier.padding(bottom = 10.dp),
                    )
                    Segmented(listOf(AskMode.NEW_PLACES to "New places", AskMode.EVERY to "Every payment"), s.askMode) { m -> Repo.updateSettings { it.copy(askMode = m) } }
                }
                Divider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text("Watch", style = T.title, color = P.fg)
                    Text(
                        if (s.scope == Scope.SMART) "Banks, wallets, bank texts, and shopping, food and ride apps" else "Only bank and wallet apps and bank texts",
                        style = T.small, color = P.muted, modifier = Modifier.padding(bottom = 10.dp),
                    )
                    Segmented(listOf(Scope.SMART to "All payment apps", Scope.FINANCE_ONLY to "Banks & wallets"), s.scope) { v -> Repo.updateSettings { it.copy(scope = v) } }
                }
                Divider()
                SettingRow("check", "Confirm auto-filed payments", "A quiet notification with Change and Regret buttons", trailing = {
                    KSwitch(s.confirmAuto) { v -> Repo.updateSettings { it.copy(confirmAuto = v) } }
                })
                Divider()
                SettingRow("in", "Track money coming in", "Salary, client payouts and transfers", trailing = {
                    KSwitch(s.detectIncome) { v -> Repo.updateSettings { it.copy(detectIncome = v) } }
                })
                Divider()
                SettingRow("apps", "Apps that paid", if (apps.isEmpty()) "None yet" else "${apps.size} app${if (apps.size == 1) "" else "s"} · mute any that cause noise", onClick = { nav.push(Page.SOURCES) })
                Divider()
                SettingRow("flask", "Test detection", "Paste any notification and see what Kharcha reads", onClick = { nav.push(Page.LAB) })
            }
        }

        item(key = "organize") {
            GroupLabel("Organize")
            Group {
                SettingRow("tag", "Categories", "Names, icons, colors and budgets", onClick = { nav.push(Page.CATEGORIES) })
                Divider()
                SettingRow("sparkle", "Remembered places", if (rules.isEmpty()) "Places file themselves after you tag them once" else "${rules.size} place${if (rules.size == 1) "" else "s"} file themselves", onClick = { nav.push(Page.PLACES) })
            }
        }

        item(key = "look") {
            GroupLabel("Look and privacy")
            Group {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text("Theme", style = T.title, color = P.fg, modifier = Modifier.padding(bottom = 10.dp))
                    Segmented(listOf(ThemeMode.SYSTEM to "System", ThemeMode.DARK to "Dark", ThemeMode.LIGHT to "Light"), s.theme) { v -> Repo.updateSettings { it.copy(theme = v) } }
                }
                Divider()
                val canLock = nav.activity?.canUseDeviceAuth() == true
                SettingRow("lock", "App lock", if (canLock) "Fingerprint, face or screen lock to open" else "Set a screen lock on your phone first", trailing = {
                    KSwitch(s.lock && canLock) { v ->
                        if (!canLock) return@KSwitch
                        if (v) nav.activity?.lockTurnedOn()
                        Repo.updateSettings { it.copy(lock = v) }
                    }
                })
                Divider()
                SettingRow("moon", "Nightly recap", "A one-line summary of the day at 9 pm", trailing = {
                    KSwitch(s.recap) { v ->
                        Repo.updateSettings { it.copy(recap = v) }
                        RecapWorker.schedule(ctx, v)
                    }
                })
            }
        }

        item(key = "data") {
            GroupLabel("Data")
            Group {
                SettingRow("export", "Export to spreadsheet", "Every payment as a CSV file", onClick = {
                    nav.activity?.markExternalTrip()
                    exportLauncher.launch("kharcha-${java.time.LocalDate.now()}.csv")
                })
                Divider()
                if (txs.any { it.sample }) {
                    SettingRow("trash", "Remove sample payments", "Keeps everything real", onClick = {
                        Repo.scope.launch { Repo.removeSample() }
                        toaster.show("Sample payments removed")
                    })
                } else {
                    SettingRow("sparkle", "Load sample payments", "Two months of examples to explore the app", onClick = {
                        Repo.scope.launch { Repo.loadSample() }
                        toaster.show("Loaded sample payments. Remove them here anytime.")
                    })
                }
            }
            Spacer(Modifier.height(12.dp))
            ConfirmButton("Delete all data", "Tap again to delete everything", Modifier.fillMaxWidth()) {
                Repo.scope.launch { Repo.clearAll() }
                toaster.show("All payments, places and categories reset")
            }
            Text(
                "Kharcha 1.0 · Everything stays on this phone. Nothing is uploaded.",
                style = T.small, color = P.faint, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
            )
        }
    }
}

@Composable
fun GroupLabel(text: String) {
    Eyebrow(text, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp, start = 4.dp))
}

@Composable
fun Stepper(value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(P.surface2).border(1.dp, P.line, RoundedCornerShape(14.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).press(enabled = value > range.first) { onChange(value - 1) }, contentAlignment = Alignment.Center) {
            Text("−", style = T.h2, color = if (value > range.first) P.fg else P.faint)
        }
        Text("$value", style = T.num.copy(fontSize = T.num.fontSize * 1.15f), color = P.fg, modifier = Modifier.width(34.dp), textAlign = TextAlign.Center)
        Box(Modifier.size(42.dp).press(enabled = value < range.last) { onChange(value + 1) }, contentAlignment = Alignment.Center) {
            Text("+", style = T.h2, color = if (value < range.last) P.fg else P.faint)
        }
    }
}
