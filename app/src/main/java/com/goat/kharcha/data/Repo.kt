package com.goat.kharcha.data

import android.content.Context
import android.content.SharedPreferences
import com.goat.kharcha.core.AskMode
import com.goat.kharcha.core.Category
import com.goat.kharcha.core.Cycle
import com.goat.kharcha.core.Flow
import com.goat.kharcha.core.Linker
import com.goat.kharcha.core.Mood
import com.goat.kharcha.core.Sample
import com.goat.kharcha.core.Scope
import com.goat.kharcha.core.Settings
import com.goat.kharcha.core.Suggest
import com.goat.kharcha.core.ThemeMode
import com.goat.kharcha.core.Tx
import com.goat.kharcha.core.merchantKey
import com.goat.kharcha.widget.SafeWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** What happened to a detected payment after it was stored. */
data class Ingested(val tx: Tx, val isNew: Boolean, val autoTagged: Boolean)

/**
 * Single source of truth. Everything lives in memory as StateFlows backed by SQLite,
 * so screens, the notification listener and the widget all see the same data.
 */
object Repo {
    private lateinit var app: Context
    private lateinit var db: Db
    private lateinit var prefs: SharedPreferences
    private val mutex = Mutex()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tx = MutableStateFlow<List<Tx>>(emptyList())
    val tx: StateFlow<List<Tx>> = _tx.asStateFlow()
    private val _cats = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _cats.asStateFlow()
    private val _rules = MutableStateFlow<Map<String, String>>(emptyMap())
    val rules: StateFlow<Map<String, String>> = _rules.asStateFlow()
    private val _apps = MutableStateFlow<List<AppSource>>(emptyList())
    val apps: StateFlow<List<AppSource>> = _apps.asStateFlow()
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    @Volatile private var initialized = false
    private var widgetJob: Job? = null

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        app = context.applicationContext
        db = Db(app)
        prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _settings.value = readSettings()
        initialized = true
        scope.launch {
            mutex.withLock { reloadAll() }
            _loaded.value = true
        }
    }

    suspend fun awaitLoaded() {
        loaded.first { it }
    }

    private fun reloadAll() {
        _cats.value = db.categories()
        _rules.value = db.rules()
        _apps.value = db.apps()
        _tx.value = db.allTx()
    }

    private fun changed() {
        widgetJob?.cancel()
        widgetJob = scope.launch {
            delay(250)
            SafeWidget.refresh(app)
        }
    }

    fun category(id: String?): Category? = if (id == null) null else _cats.value.firstOrNull { it.id == id }
    fun find(id: Long): Tx? = _tx.value.firstOrNull { it.id == id }

    // ---------- payments ----------

    /** Stores a payment the listener found, merging it with its twin from another app if there is one. */
    suspend fun ingest(incoming: Tx, pkg: String, fromMerchantApp: Boolean): Ingested? = mutex.withLock {
        val s = _settings.value
        val autoRule = { t: Tx ->
            if (t.flow == Flow.OUT && t.categoryId == null && s.askMode == AskMode.NEW_PLACES) {
                _rules.value[merchantKey(t.merchant)]?.takeIf { id -> _cats.value.any { it.id == id } }
            } else null
        }
        when (val d = Linker.decide(_tx.value, incoming, pkg, fromMerchantApp)) {
            Linker.Decision.Skip -> null
            is Linker.Decision.Insert -> {
                var t = d.tx.copy(packages = pkg)
                var auto = false
                if (t.flow == Flow.IN) {
                    val guess = Suggest.incomeCategory("${t.merchant} ${t.source} ${t.raw}")
                    t = t.copy(categoryId = guess.takeIf { g -> _cats.value.any { it.id == g } } ?: _cats.value.firstOrNull { it.income }?.id)
                    auto = true
                } else {
                    autoRule(t)?.let { t = t.copy(categoryId = it); auto = true }
                }
                val id = db.insertTx(t)
                t = t.copy(id = id)
                _tx.value = listOf(t) + _tx.value
                changed()
                Ingested(t, isNew = true, autoTagged = auto)
            }
            is Linker.Decision.Merge -> {
                var t = d.tx
                var auto = false
                autoRule(t)?.let { t = t.copy(categoryId = it); auto = true }
                db.updateTx(t)
                _tx.value = _tx.value.map { if (it.id == t.id) t else it }
                changed()
                Ingested(t, isNew = false, autoTagged = auto)
            }
        }
    }

    suspend fun save(t: Tx, remember: Boolean = false): Tx = mutex.withLock {
        val saved = if (t.id > 0 && _tx.value.any { it.id == t.id }) {
            db.updateTx(t)
            _tx.value = _tx.value.map { if (it.id == t.id) t else it }.sortedByDescending { it.time }
            t
        } else {
            val id = db.insertTx(t.copy(id = 0))
            val n = t.copy(id = id)
            _tx.value = (_tx.value + n).sortedByDescending { it.time }
            n
        }
        if (remember && saved.flow == Flow.OUT && saved.categoryId != null && !saved.merchant.isNullOrBlank()) {
            putRuleLocked(saved.merchant, saved.categoryId)
        }
        changed()
        saved
    }

    /** Files a payment under a category in one step, from a notification button or the quick queue. */
    suspend fun tag(id: Long, categoryId: String, remember: Boolean = true): Tx? {
        val t = find(id) ?: return null
        return save(t.copy(categoryId = categoryId), remember)
    }

    suspend fun setMood(id: Long, mood: Mood) {
        val t = find(id) ?: return
        save(t.copy(mood = mood))
    }

    suspend fun delete(id: Long): Tx? = mutex.withLock {
        val t = _tx.value.firstOrNull { it.id == id } ?: return@withLock null
        db.deleteTx(id)
        _tx.value = _tx.value.filter { it.id != id }
        changed()
        t
    }

    /** Puts a deleted payment back exactly as it was, same id included. */
    suspend fun restore(t: Tx) = mutex.withLock {
        if (_tx.value.any { it.id == t.id }) return@withLock
        db.insertTx(t)
        _tx.value = (_tx.value + t).sortedByDescending { it.time }
        changed()
    }

    // ---------- rules ----------

    private fun putRuleLocked(merchant: String, categoryId: String) {
        val k = merchantKey(merchant)
        if (k.length < 2) return
        db.putRule(k, categoryId)
        _rules.value = _rules.value + (k to categoryId)
    }

    suspend fun setRule(key: String, categoryId: String) = mutex.withLock {
        db.putRule(key, categoryId)
        _rules.value = _rules.value + (key to categoryId)
    }

    suspend fun forgetRule(key: String) = mutex.withLock {
        db.deleteRule(key)
        _rules.value = _rules.value - key
    }

    // ---------- categories ----------

    suspend fun saveCategory(c: Category) = mutex.withLock {
        db.upsertCategory(c)
        _cats.value = db.categories()
        changed()
    }

    suspend fun moveCategory(id: String, up: Boolean) = mutex.withLock {
        val c = _cats.value.firstOrNull { it.id == id } ?: return@withLock
        val group = _cats.value.filter { it.income == c.income }.sortedBy { it.order }.toMutableList()
        val i = group.indexOfFirst { it.id == id }
        val j = if (up) i - 1 else i + 1
        if (j !in group.indices) return@withLock
        val tmp = group[i]; group[i] = group[j]; group[j] = tmp
        val base = if (c.income) 20 else 0
        group.forEachIndexed { idx, cat -> db.upsertCategory(cat.copy(order = base + idx)) }
        _cats.value = db.categories()
    }

    /** Deletes a category. Its payments move to [moveTo], or back to "needs a reason" when null. */
    suspend fun deleteCategory(id: String, moveTo: String?) = mutex.withLock {
        db.recategorize(id, moveTo)
        db.deleteCategory(id)
        _cats.value = db.categories()
        _rules.value = db.rules()
        _tx.value = db.allTx()
        changed()
    }

    // ---------- apps ----------

    suspend fun recordApp(pkg: String, label: String, kind: String, time: Long) = mutex.withLock {
        db.recordApp(pkg, label, kind, time)
        _apps.value = db.apps()
    }

    suspend fun setMuted(pkg: String, muted: Boolean) = mutex.withLock {
        db.setMuted(pkg, muted)
        _apps.value = db.apps()
    }

    fun isMuted(pkg: String): Boolean = _apps.value.any { it.pkg == pkg && it.muted }

    // ---------- data ----------

    suspend fun loadSample() = mutex.withLock {
        db.deleteSample()
        db.insertMany(Sample.generate(LocalDate.now()))
        _tx.value = db.allTx()
        changed()
    }

    suspend fun removeSample() = mutex.withLock {
        db.deleteSample()
        _tx.value = db.allTx()
        changed()
    }

    fun hasSample(): Boolean = _tx.value.any { it.sample }

    suspend fun clearAll() = mutex.withLock {
        db.clearAll()
        reloadAll()
        changed()
    }

    fun csv(): String {
        val cats = _cats.value.associateBy { it.id }
        val f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val zone = java.time.ZoneId.systemDefault()
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        val sb = StringBuilder("Date,Type,Amount (Rs),Payee,Category,Worth it,Note,Owed by,Detected from\n")
        for (t in _tx.value.sortedBy { it.time }) {
            val d = java.time.Instant.ofEpochMilli(t.time).atZone(zone).format(f)
            val amt = (if (t.flow == Flow.IN) "" else "-") + "${t.amount / 100}.${(t.amount % 100).toString().padStart(2, '0')}"
            sb.append(listOf(
                d, if (t.flow == Flow.IN) "Income" else "Expense", amt, q(t.payee),
                q(cats[t.categoryId]?.name ?: "Needs a reason"), t.mood.label, q(t.note),
                q(if (t.owed) t.owedBy.ifBlank { "Someone" } else ""), q(if (t.manual) "Added by hand" else t.source),
            ).joinToString(",")).append('\n')
        }
        return sb.toString()
    }

    // ---------- settings ----------

    fun updateSettings(transform: (Settings) -> Settings) {
        val n = transform(_settings.value)
        _settings.value = n
        prefs.edit()
            .putString("name", n.name)
            .putLong("budget", n.budget)
            .putInt("cycleDay", n.cycleDay)
            .putLong("hourlyRate", n.hourlyRate)
            .putString("askMode", n.askMode.name)
            .putBoolean("confirmAuto", n.confirmAuto)
            .putBoolean("detectIncome", n.detectIncome)
            .putString("scope", n.scope.name)
            .putBoolean("lock", n.lock)
            .putBoolean("recap", n.recap)
            .putString("theme", n.theme.name)
            .putBoolean("onboarded", n.onboarded)
            .apply()
        changed()
    }

    private fun readSettings(): Settings = Settings(
        name = prefs.getString("name", "") ?: "",
        budget = prefs.getLong("budget", 0),
        cycleDay = prefs.getInt("cycleDay", 1),
        hourlyRate = prefs.getLong("hourlyRate", 0),
        askMode = enumOr(prefs.getString("askMode", null), AskMode.NEW_PLACES),
        confirmAuto = prefs.getBoolean("confirmAuto", true),
        detectIncome = prefs.getBoolean("detectIncome", true),
        scope = enumOr(prefs.getString("scope", null), Scope.SMART),
        lock = prefs.getBoolean("lock", false),
        recap = prefs.getBoolean("recap", true),
        theme = enumOr(prefs.getString("theme", null), ThemeMode.SYSTEM),
        onboarded = prefs.getBoolean("onboarded", false),
    )

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: fallback

    fun today(): LocalDate = Cycle.date(System.currentTimeMillis())
}
