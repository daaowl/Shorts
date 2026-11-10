package com.goat.kharcha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goat.kharcha.core.AppKind
import com.goat.kharcha.core.Category
import com.goat.kharcha.core.Defaults
import com.goat.kharcha.core.Flow
import com.goat.kharcha.core.Money
import com.goat.kharcha.core.Parser
import com.goat.kharcha.core.merchantKey
import com.goat.kharcha.data.Repo
import kotlinx.coroutines.launch

@Composable
private fun pagePadding(): PaddingValues {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(start = 18.dp, end = 18.dp, top = top + 10.dp, bottom = bottom + 40.dp)
}

// ---------- categories ----------

@Composable
fun CategoriesScreen() {
    val nav = LocalNav.current
    val cats by Repo.categories.collectAsState()
    var editing by remember { mutableStateOf<Category?>(null) }
    var open by remember { mutableStateOf(false) }
    val spend = cats.filter { !it.income }.sortedBy { it.order }
    val income = cats.filter { it.income }.sortedBy { it.order }
    val totalBudget = spend.sumOf { it.budget }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
            item { ScreenHeader("Categories", "Tap one to rename, recolor or set its budget", onBack = nav.back) }
            item {
                GroupLabel("Spending" + if (totalBudget > 0) " · budgets add up to ${Money.round(totalBudget)}" else "")
            }
            item { CategoryList(spend) { editing = it; open = true } }
            item {
                SecondaryButton("New spending category", Modifier.fillMaxWidth().padding(top = 10.dp), icon = "plus") {
                    editing = Category("c_" + System.currentTimeMillis(), "", Defaults.palette.random(), "star", 0, (spend.maxOfOrNull { it.order } ?: -1) + 1, false)
                    open = true
                }
            }
            item { GroupLabel("Money in") }
            item { CategoryList(income) { editing = it; open = true } }
            item {
                SecondaryButton("New income category", Modifier.fillMaxWidth().padding(top = 10.dp), icon = "plus") {
                    editing = Category("c_" + System.currentTimeMillis(), "", Defaults.palette.random(), "banknote", 0, (income.maxOfOrNull { it.order } ?: 19) + 1, true)
                    open = true
                }
            }
        }
        Sheet(visible = open, onDismiss = { open = false }) {
            editing?.let { c -> key(c.id) { CategoryEditor(c, cats) { open = false } } }
        }
    }
}

@Composable
private fun CategoryList(list: List<Category>, onEdit: (Category) -> Unit) {
    val txs by Repo.tx.collectAsState()
    Group {
        list.forEachIndexed { i, c ->
            if (i > 0) Divider()
            val count = txs.count { it.categoryId == c.id }
            Row(
                Modifier.fillMaxWidth().clickable { onEdit(c) }.padding(start = 16.dp, end = 8.dp, top = 11.dp, bottom = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CatIcon(c, size = 36.dp, iconSize = 19.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = T.title, color = P.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(if (!c.income && c.budget > 0) "${Money.round(c.budget)} a month" else null, "$count payment${if (count == 1) "" else "s"}").joinToString(" · "),
                        style = T.small, color = P.muted,
                    )
                }
                IconButton("up", size = 34.dp, bg = Color.Transparent, tint = if (i > 0) P.muted else P.line, contentDescription = "Move up") {
                    if (i > 0) Repo.scope.launch { Repo.moveCategory(c.id, up = true) }
                }
                IconButton("down", size = 34.dp, bg = Color.Transparent, tint = if (i < list.lastIndex) P.muted else P.line, contentDescription = "Move down") {
                    if (i < list.lastIndex) Repo.scope.launch { Repo.moveCategory(c.id, up = false) }
                }
            }
        }
    }
}

@Composable
private fun CategoryEditor(initial: Category, all: List<Category>, onDone: () -> Unit) {
    val toaster = LocalToaster.current
    val exists = all.any { it.id == initial.id }
    var name by remember { mutableStateOf(initial.name) }
    var icon by remember { mutableStateOf(initial.icon) }
    var color by remember { mutableStateOf(initial.color) }
    var budgetText by remember { mutableStateOf(if (initial.budget > 0) Money.toInput(initial.budget) else "") }
    val preview = initial.copy(name = name, icon = icon, color = color)
    val sameGroup = all.filter { it.income == initial.income }
    val canDelete = exists && sameGroup.size > 1

    Row(verticalAlignment = Alignment.CenterVertically) {
        CatIcon(preview, size = 48.dp, iconSize = 25.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Eyebrow(if (exists) "Edit category" else "New category")
            Text(name.ifBlank { "Untitled" }, style = T.h2, color = P.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton("close", size = 34.dp, onClick = onDone, contentDescription = "Close")
    }
    Spacer(Modifier.height(16.dp))
    Field(name, { name = it.take(18) }, label = "Name", placeholder = "Rent, Gym, Pets…", keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words))

    Text("Icon", style = T.small.copy(fontWeight = FontWeight.Bold), color = P.muted, modifier = Modifier.padding(top = 16.dp, bottom = 9.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Glyphs.categoryIcons.chunked(8).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { ic ->
                    val on = ic == icon
                    val cc = catColor(color, P.dark)
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)).clickable { icon = ic }
                            .background(if (on) cc.copy(alpha = 0.18f) else P.surface2)
                            .border(1.5.dp, if (on) cc else Color.Transparent, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Glyph(ic, if (on) cc else P.muted, 19.dp) }
                }
            }
        }
    }

    Text("Color", style = T.small.copy(fontWeight = FontWeight.Bold), color = P.muted, modifier = Modifier.padding(top = 16.dp, bottom = 9.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Defaults.palette.forEach { argb ->
            val on = argb == color
            Box(
                Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).clickable { color = argb }
                    .background(catColor(argb, P.dark))
                    .border(2.5.dp, if (on) P.fg else Color.Transparent, CircleShape),
            )
        }
    }

    if (!initial.income) {
        Spacer(Modifier.height(16.dp))
        Field(
            budgetText, { v -> budgetText = v.filter { it.isDigit() }.take(9) },
            label = "Monthly budget", placeholder = "No budget",
            leading = { Text("Rs", style = T.title, color = P.muted) },
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }

    if (canDelete) {
        val moveTo = sameGroup.firstOrNull { (it.id == "other" || it.id == "transfer_in") && it.id != initial.id }
        Text(
            "Deleting moves its payments to ${moveTo?.name ?: "Needs a reason"}.",
            style = T.small, color = P.muted, modifier = Modifier.padding(top = 18.dp),
        )
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (canDelete) {
            ConfirmButton("Delete", "Tap to delete", Modifier.weight(0.42f)) {
                val moveTo = sameGroup.firstOrNull { (it.id == "other" || it.id == "transfer_in") && it.id != initial.id }?.id
                Repo.scope.launch { Repo.deleteCategory(initial.id, moveTo) }
                onDone()
                toaster.show("Deleted ${initial.name}")
            }
        }
        PrimaryButton(if (exists) "Save" else "Create", Modifier.weight(0.58f), enabled = name.isNotBlank()) {
            val c = initial.copy(name = name.trim(), icon = icon, color = color, budget = if (initial.income) 0 else Money.parseInput(budgetText) ?: 0)
            Repo.scope.launch { Repo.saveCategory(c) }
            onDone()
            toaster.show(if (exists) "Saved ${c.name}" else "Created ${c.name}")
        }
    }
}

// ---------- remembered places ----------

@Composable
fun PlacesScreen() {
    val nav = LocalNav.current
    val rules by Repo.rules.collectAsState()
    val cats by Repo.categories.collectAsState()
    val txs by Repo.tx.collectAsState()
    var editing by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf(false) }
    val names = remember(txs) {
        val m = HashMap<String, String>()
        txs.forEach { t -> t.merchant?.let { m.putIfAbsent(merchantKey(it), it) } }
        m
    }
    val list = remember(rules, names) { rules.entries.sortedBy { (names[it.key] ?: it.key).lowercase() } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
            item { ScreenHeader("Remembered places", "Payments here file themselves", onBack = nav.back) }
            if (list.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Glyph("sparkle", P.accent, 26.dp)
                        Text("Nothing remembered yet", style = T.h2, color = P.fg, modifier = Modifier.padding(top = 12.dp))
                        Text("Tag a payment once and Kharcha remembers that place. The next payment there is filed without asking.", style = T.body, color = P.muted, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            items(list, key = { it.key }) { (k, catId) ->
                val c = cats.firstOrNull { it.id == catId }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { editing = k; open = true }.padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CatIcon(c)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(names[k] ?: k, style = T.title, color = P.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Files under ${c?.name ?: "a deleted category"}", style = T.small, color = P.muted)
                    }
                    Glyph("chevron", P.faint, 18.dp)
                }
            }
        }
        Sheet(visible = open, onDismiss = { open = false }) {
            val k = editing
            if (k != null) {
                val current = rules[k]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Eyebrow("Remembered place")
                        Text(names[k] ?: k, style = T.h2, color = P.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton("close", size = 34.dp, onClick = { open = false }, contentDescription = "Close")
                }
                Text("File future payments here under", style = T.small.copy(fontWeight = FontWeight.Bold), color = P.muted, modifier = Modifier.padding(top = 16.dp, bottom = 9.dp))
                CategoryGrid(cats.filter { !it.income }.sortedBy { it.order }, current) { id ->
                    Repo.scope.launch { Repo.setRule(k, id) }
                    open = false
                }
                ConfirmButton("Forget this place", "Tap again to forget", Modifier.fillMaxWidth().padding(top = 18.dp)) {
                    Repo.scope.launch { Repo.forgetRule(k) }
                    open = false
                }
            }
        }
    }
}

// ---------- apps that paid ----------

@Composable
fun SourcesScreen() {
    val nav = LocalNav.current
    val apps by Repo.apps.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
        item { ScreenHeader("Apps that paid", "Every app that has posted a payment", onBack = nav.back) }
        if (apps.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Glyph("apps", P.accent, 26.dp)
                    Text("No apps yet", style = T.h2, color = P.fg, modifier = Modifier.padding(top = 12.dp))
                    Text(
                        "Banks, wallets, SMS apps and shops show up here after their first payment notification. Mute any app that sends false alarms.",
                        style = T.body, color = P.muted, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        } else {
            item {
                Group {
                    apps.forEachIndexed { i, a ->
                        if (i > 0) Divider()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(P.surface2), contentAlignment = Alignment.Center) {
                                Text(a.label.take(1).uppercase(), style = T.title, color = if (a.muted) P.faint else P.fg)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.label, style = T.title, color = if (a.muted) P.muted else P.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val kind = when (a.kind) {
                                    AppKind.FINANCE.name -> "Bank or wallet"
                                    AppKind.SMS.name -> "Text messages"
                                    AppKind.MERCHANT.name -> "Shop or service"
                                    else -> "Other app"
                                }
                                Text(
                                    if (a.muted) "Muted · ignored from now on" else "$kind · ${a.hits} payment${if (a.hits == 1) "" else "s"} · last ${dateOf(a.last)}",
                                    style = T.small, color = P.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            KSwitch(!a.muted) { on -> Repo.scope.launch { Repo.setMuted(a.pkg, !on) } }
                        }
                    }
                }
            }
        }
    }
}

// ---------- detection lab ----------

@Composable
fun LabScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    var text by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(AppKind.SMS) }
    val settings by Repo.settings.collectAsState()
    val examples = listOf(
        "Card" to "HBL: PKR 1,850.00 debited from A/C **4417 at CHEEZIOUS WAH CANTT on 10-Nov. Avl Bal PKR 23,450",
        "Wallet" to "Rs 1,500 sent to ALI HASSAN 03001234567 via JazzCash. TID: 88412",
        "Money in" to "You have received Rs. 25,000 from PAYONEER in your Meezan A/C",
        "Promo" to "Get 20% off upto Rs 500 on Foodpanda with UBL cards",
        "OTP" to "Your OTP for a transaction of PKR 5,000 at DARAZ is 123456",
    )
    val label = when (kind) { AppKind.FINANCE -> "Test bank"; AppKind.MERCHANT -> "Test shop"; else -> "Messages" }
    val result = remember(text, kind, settings.scope) {
        if (text.isBlank()) null else Parser.parse("lab", label, null, text, kind, settings.scope)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding()) {
        item { ScreenHeader("Test detection", "See exactly what Kharcha reads from a notification", onBack = nav.back) }
        item {
            Segmented(listOf(AppKind.SMS to "Bank text", AppKind.FINANCE to "Bank app", AppKind.MERCHANT to "Shop app"), kind) { kind = it }
            Spacer(Modifier.height(12.dp))
            Field(text, { text = it.take(400) }, placeholder = "Paste or type a notification", singleLine = false, minLines = 4)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                examples.take(3).forEach { (n, e) -> Chip(n, selected = text == e) { text = e } }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                examples.drop(3).forEach { (n, e) -> Chip(n, selected = text == e) { text = e } }
            }
        }
        item {
            Spacer(Modifier.height(18.dp))
            if (text.isBlank()) {
                Text("Kharcha needs an amount in Rs or PKR and a word like paid, spent, debited or received. OTPs, offers, failed payments and bill reminders are skipped on purpose.", style = T.body, color = P.muted)
            } else if (result == null) {
                Card(Modifier.fillMaxWidth(), border = P.line) {
                    Eyebrow("Ignored", color = P.muted)
                    Text("Not a payment", style = T.h2, color = P.fg, modifier = Modifier.padding(top = 6.dp))
                    Text("No amount with a payment word, or it reads like an offer, OTP, reminder or failed payment.", style = T.body, color = P.muted, modifier = Modifier.padding(top = 4.dp))
                }
            } else {
                val inflow = result.flow == Flow.IN
                Card(Modifier.fillMaxWidth(), border = (if (inflow) P.good else P.accent).copy(alpha = 0.5f)) {
                    Eyebrow(if (inflow) "Money in" else "Money out", color = if (inflow) P.good else P.accent)
                    Text(Money.format(result.amount), style = T.amountXl, color = P.fg, modifier = Modifier.padding(top = 4.dp))
                    LabRow("Payee", result.merchant ?: "Not in the text, so Kharcha will ask")
                    LabRow("Detected from", result.source)
                    val rule = Repo.rules.value[merchantKey(result.merchant)]?.let { Repo.category(it)?.name }
                    LabRow("Category", rule?.let { "$it (remembered)" } ?: if (inflow) "Filed as income" else "Kharcha will ask")
                }
                SecondaryButton("Run it as a real payment", Modifier.fillMaxWidth().padding(top = 12.dp), icon = "bolt") {
                    TestPayment.send(ctx, text)
                    toaster.show("Sent through detection. Check your notifications.")
                }
            }
        }
    }
}

@Composable
private fun LabRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(k, style = T.small, color = P.muted, modifier = Modifier.width(110.dp))
        Text(v, style = T.small.copy(fontWeight = FontWeight.Bold), color = P.fg, modifier = Modifier.weight(1f))
    }
}
