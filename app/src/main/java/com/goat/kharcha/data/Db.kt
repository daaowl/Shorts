package com.goat.kharcha.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.goat.kharcha.core.Category
import com.goat.kharcha.core.Defaults
import com.goat.kharcha.core.Flow
import com.goat.kharcha.core.Mood
import com.goat.kharcha.core.Tx

/** An app that has posted at least one payment notification. */
data class AppSource(
    val pkg: String,
    val label: String,
    val kind: String,
    val hits: Int,
    val last: Long,
    val muted: Boolean,
)

class Db(context: Context) : SQLiteOpenHelper(context, "kharcha.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE tx(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                amount INTEGER NOT NULL, flow INTEGER NOT NULL, merchant TEXT, category TEXT,
                time INTEGER NOT NULL, source TEXT NOT NULL DEFAULT '', packages TEXT NOT NULL DEFAULT '',
                raw TEXT NOT NULL DEFAULT '', mood INTEGER NOT NULL DEFAULT 0, note TEXT NOT NULL DEFAULT '',
                owed INTEGER NOT NULL DEFAULT 0, owed_by TEXT NOT NULL DEFAULT '',
                manual INTEGER NOT NULL DEFAULT 0, sample INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL("CREATE INDEX tx_time ON tx(time)")
        db.execSQL(
            """CREATE TABLE category(
                id TEXT PRIMARY KEY, name TEXT NOT NULL, color INTEGER NOT NULL, icon TEXT NOT NULL,
                budget INTEGER NOT NULL DEFAULT 0, ord INTEGER NOT NULL DEFAULT 0, income INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL("CREATE TABLE rule(key TEXT PRIMARY KEY, category TEXT NOT NULL)")
        db.execSQL(
            """CREATE TABLE app(pkg TEXT PRIMARY KEY, label TEXT NOT NULL, kind TEXT NOT NULL,
                hits INTEGER NOT NULL DEFAULT 0, last INTEGER NOT NULL DEFAULT 0, muted INTEGER NOT NULL DEFAULT 0)"""
        )
        Defaults.categories.forEach { db.insert("category", null, it.values()) }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    // ---------- tx ----------

    fun allTx(): List<Tx> = readableDatabase.query("tx", null, null, null, null, null, "time DESC").use { c ->
        val out = ArrayList<Tx>(c.count)
        while (c.moveToNext()) out += c.toTx()
        out
    }

    fun insertTx(t: Tx): Long = writableDatabase.insertOrThrow("tx", null, t.values(withId = t.id > 0))

    fun updateTx(t: Tx) {
        writableDatabase.update("tx", t.values(withId = false), "id=?", arrayOf(t.id.toString()))
    }

    fun deleteTx(id: Long) {
        writableDatabase.delete("tx", "id=?", arrayOf(id.toString()))
    }

    fun deleteSample() {
        writableDatabase.delete("tx", "sample=1", null)
    }

    fun insertMany(list: List<Tx>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            list.forEach { db.insertOrThrow("tx", null, it.values(withId = false)) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun recategorize(from: String, to: String?) {
        val cv = ContentValues().apply { if (to == null) putNull("category") else put("category", to) }
        writableDatabase.update("tx", cv, "category=?", arrayOf(from))
        writableDatabase.delete("rule", "category=?", arrayOf(from))
    }

    fun clearAll() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("tx", null, null)
            db.delete("rule", null, null)
            db.delete("app", null, null)
            db.delete("category", null, null)
            Defaults.categories.forEach { db.insert("category", null, it.values()) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---------- categories ----------

    fun categories(): List<Category> = readableDatabase.query("category", null, null, null, null, null, "ord ASC").use { c ->
        val out = ArrayList<Category>()
        while (c.moveToNext()) {
            out += Category(
                id = c.str("id"), name = c.str("name"), color = c.long("color"), icon = c.str("icon"),
                budget = c.long("budget"), order = c.int("ord"), income = c.int("income") == 1,
            )
        }
        out
    }

    fun upsertCategory(cat: Category) {
        writableDatabase.insertWithOnConflict("category", null, cat.values(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteCategory(id: String) {
        writableDatabase.delete("category", "id=?", arrayOf(id))
    }

    // ---------- rules ----------

    fun rules(): Map<String, String> = readableDatabase.query("rule", null, null, null, null, null, null).use { c ->
        val out = HashMap<String, String>()
        while (c.moveToNext()) out[c.str("key")] = c.str("category")
        out
    }

    fun putRule(key: String, category: String) {
        val cv = ContentValues().apply { put("key", key); put("category", category) }
        writableDatabase.insertWithOnConflict("rule", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteRule(key: String) {
        writableDatabase.delete("rule", "key=?", arrayOf(key))
    }

    // ---------- apps ----------

    fun apps(): List<AppSource> = readableDatabase.query("app", null, null, null, null, null, "last DESC").use { c ->
        val out = ArrayList<AppSource>()
        while (c.moveToNext()) {
            out += AppSource(c.str("pkg"), c.str("label"), c.str("kind"), c.int("hits"), c.long("last"), c.int("muted") == 1)
        }
        out
    }

    fun recordApp(pkg: String, label: String, kind: String, time: Long) {
        // Plain UPDATE then INSERT: SQLite's upsert syntax isn't available before Android 11.
        val db = writableDatabase
        val changed = db.compileStatement("UPDATE app SET label=?, kind=?, hits=hits+1, last=? WHERE pkg=?").use { st ->
            st.bindString(1, label); st.bindString(2, kind); st.bindLong(3, time); st.bindString(4, pkg)
            st.executeUpdateDelete()
        }
        if (changed == 0) {
            val cv = ContentValues().apply {
                put("pkg", pkg); put("label", label); put("kind", kind); put("hits", 1); put("last", time); put("muted", 0)
            }
            db.insertWithOnConflict("app", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun setMuted(pkg: String, muted: Boolean) {
        val cv = ContentValues().apply { put("muted", if (muted) 1 else 0) }
        writableDatabase.update("app", cv, "pkg=?", arrayOf(pkg))
    }

    // ---------- mapping ----------

    private fun Tx.values(withId: Boolean) = ContentValues().apply {
        if (withId) put("id", id)
        put("amount", amount)
        put("flow", if (flow == Flow.IN) 1 else 0)
        if (merchant == null) putNull("merchant") else put("merchant", merchant)
        if (categoryId == null) putNull("category") else put("category", categoryId)
        put("time", time)
        put("source", source)
        put("packages", packages)
        put("raw", raw)
        put("mood", mood.ordinal)
        put("note", note)
        put("owed", if (owed) 1 else 0)
        put("owed_by", owedBy)
        put("manual", if (manual) 1 else 0)
        put("sample", if (sample) 1 else 0)
    }

    private fun Category.values() = ContentValues().apply {
        put("id", id); put("name", name); put("color", color); put("icon", icon)
        put("budget", budget); put("ord", order); put("income", if (income) 1 else 0)
    }

    private fun Cursor.toTx() = Tx(
        id = long("id"),
        amount = long("amount"),
        flow = if (int("flow") == 1) Flow.IN else Flow.OUT,
        merchant = strOrNull("merchant"),
        categoryId = strOrNull("category"),
        time = long("time"),
        source = str("source"),
        packages = str("packages"),
        raw = str("raw"),
        mood = Mood.values().getOrElse(int("mood")) { Mood.NONE },
        note = str("note"),
        owed = int("owed") == 1,
        owedBy = str("owed_by"),
        manual = int("manual") == 1,
        sample = int("sample") == 1,
    )

    private fun Cursor.idx(n: String) = getColumnIndexOrThrow(n)
    private fun Cursor.str(n: String): String = getString(idx(n)) ?: ""
    private fun Cursor.strOrNull(n: String): String? = idx(n).let { if (isNull(it)) null else getString(it) }
    private fun Cursor.long(n: String): Long = getLong(idx(n))
    private fun Cursor.int(n: String): Int = getInt(idx(n))
}
