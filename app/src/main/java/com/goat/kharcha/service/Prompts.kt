package com.goat.kharcha.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.goat.kharcha.MainActivity
import com.goat.kharcha.R
import com.goat.kharcha.TagActivity
import com.goat.kharcha.core.Flow
import com.goat.kharcha.core.Money
import com.goat.kharcha.core.Mood
import com.goat.kharcha.core.Suggest
import com.goat.kharcha.core.Tx
import com.goat.kharcha.data.Ingested
import com.goat.kharcha.data.Repo
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** The "money just left, what was it for?" notifications and their one-tap buttons. */
object Prompts {
    const val CH_ASK = "ask"
    const val CH_AUTO = "auto"
    const val CH_INCOME = "income"
    const val CH_RECAP = "recap"
    private const val ACCENT = 0xFF5B66E0.toInt()

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_ASK, "Payment prompts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Asks what a new payment was for, right after it happens"
                },
                NotificationChannel(CH_AUTO, "Auto-filed payments", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Quiet confirmation when a known place is filed for you"
                },
                NotificationChannel(CH_INCOME, "Money in", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "When money arrives in an account or wallet"
                },
                NotificationChannel(CH_RECAP, "Nightly recap", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "A short summary of the day at 9 pm"
                },
            )
        )
    }

    fun canPost(ctx: Context): Boolean {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun id(txId: Long): Int = (txId % 1_000_000L).toInt() + 1000

    fun onDetected(ctx: Context, r: Ingested) {
        val t = r.tx
        when {
            t.flow == Flow.IN -> if (r.isNew) income(ctx, t)
            t.categoryId == null -> ask(ctx, t, quiet = !r.isNew)
            r.autoTagged && Repo.settings.value.confirmAuto -> filed(ctx, t)
        }
    }

    @SuppressLint("MissingPermission")
    fun ask(ctx: Context, t: Tx, quiet: Boolean = false) {
        if (!canPost(ctx)) return
        val nid = id(t.id)
        val hour = Instant.ofEpochMilli(t.time).atZone(ZoneId.systemDefault()).hour
        val cats = Repo.categories.value
        val ranked = Suggest.rank(t.merchant, "${t.source} ${t.raw}", hour, Repo.rules.value, Repo.tx.value, cats)
        val title = if (t.merchant.isNullOrBlank()) "${Money.format(t.amount)} just left" else "${Money.format(t.amount)} · ${t.merchant}"
        val body = "What was it for?"
        val quote = t.raw.split(com.goat.kharcha.core.Linker.SEP).firstOrNull()?.trim().orEmpty()
        val b = NotificationCompat.Builder(ctx, CH_ASK)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(ACCENT)
            .setContentTitle(title)
            .setContentText(body)
            .setSubText(t.source.ifBlank { null })
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (quote.isNotEmpty()) "$body\n“$quote”" else body))
            .setContentIntent(openTag(ctx, t.id, nid))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(quiet)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(t.time)
            .setShowWhen(true)
        ranked.take(2).forEachIndexed { i, cid ->
            val name = cats.firstOrNull { it.id == cid }?.name ?: return@forEachIndexed
            b.addAction(0, name, action(ctx, ActionReceiver.TAG, t.id, nid * 8 + i, cat = cid))
        }
        b.addAction(0, "More…", openTag(ctx, t.id, nid * 8 + 2))
        NotificationManagerCompat.from(ctx).notify(nid, b.build())
    }

    @SuppressLint("MissingPermission")
    private fun filed(ctx: Context, t: Tx) {
        if (!canPost(ctx)) return
        val nid = id(t.id)
        val cat = Repo.category(t.categoryId)?.name ?: return
        val b = NotificationCompat.Builder(ctx, CH_AUTO)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(ACCENT)
            .setContentTitle("${Money.format(t.amount)} · ${t.payee}")
            .setContentText("Filed under $cat")
            .setSubText(t.source.ifBlank { null })
            .setContentIntent(openTag(ctx, t.id, nid))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(6 * 60 * 60 * 1000L)
            .setWhen(t.time)
            .addAction(0, "Change", openTag(ctx, t.id, nid * 8 + 2))
            .addAction(0, "Worth it", action(ctx, ActionReceiver.MOOD, t.id, nid * 8 + 3, mood = Mood.WORTH))
            .addAction(0, "Regret", action(ctx, ActionReceiver.MOOD, t.id, nid * 8 + 4, mood = Mood.REGRET))
        NotificationManagerCompat.from(ctx).notify(nid, b.build())
    }

    @SuppressLint("MissingPermission")
    private fun income(ctx: Context, t: Tx) {
        if (!canPost(ctx)) return
        val nid = id(t.id)
        val cat = Repo.category(t.categoryId)?.name
        val from = t.merchant?.let { "From $it" } ?: "Into ${t.source}"
        val b = NotificationCompat.Builder(ctx, CH_INCOME)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(0xFF1C9862.toInt())
            .setContentTitle("${Money.format(t.amount)} came in")
            .setContentText(if (cat != null) "$from · $cat" else from)
            .setSubText(t.source.ifBlank { null })
            .setContentIntent(openTag(ctx, t.id, nid))
            .setAutoCancel(true)
            .setTimeoutAfter(12 * 60 * 60 * 1000L)
            .setWhen(t.time)
            .addAction(0, "Change", openTag(ctx, t.id, nid * 8 + 2))
        NotificationManagerCompat.from(ctx).notify(nid, b.build())
    }

    @SuppressLint("MissingPermission")
    fun recap(ctx: Context, title: String, text: String, pending: Int) {
        if (!canPost(ctx)) return
        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (pending > 0) putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_QUEUE)
        }
        val pi = PendingIntent.getActivity(ctx, 77, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(ctx, CH_RECAP)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(ACCENT)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
        if (pending > 0) b.addAction(0, "Tag $pending now", pi)
        NotificationManagerCompat.from(ctx).notify(9, b.build())
    }

    fun cancel(ctx: Context, txId: Long) {
        NotificationManagerCompat.from(ctx).cancel(id(txId))
    }

    private fun openTag(ctx: Context, txId: Long, code: Int): PendingIntent {
        val i = Intent(ctx, TagActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(TagActivity.EXTRA_TX, txId)
        }
        return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun action(ctx: Context, act: String, txId: Long, code: Int, cat: String? = null, mood: Mood? = null): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java).apply {
            action = act
            putExtra(ActionReceiver.EXTRA_TX, txId)
            cat?.let { putExtra(ActionReceiver.EXTRA_CAT, it) }
            mood?.let { putExtra(ActionReceiver.EXTRA_MOOD, it.name) }
        }
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

/** Handles the buttons on payment notifications without opening the app. */
class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "com.goat.kharcha.TAG"
        const val MOOD = "com.goat.kharcha.MOOD"
        const val EXTRA_TX = "tx"
        const val EXTRA_CAT = "cat"
        const val EXTRA_MOOD = "mood"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        Repo.init(app)
        val txId = intent.getLongExtra(EXTRA_TX, -1L)
        if (txId < 0) return
        val pending = goAsync()
        Repo.scope.launch {
            try {
                Repo.awaitLoaded()
                when (intent.action) {
                    TAG -> {
                        val cat = intent.getStringExtra(EXTRA_CAT) ?: return@launch
                        val t = Repo.tag(txId, cat, remember = true)
                        Prompts.cancel(app, txId)
                        val name = Repo.category(cat)?.name
                        if (t != null && name != null) {
                            val learns = Repo.settings.value.askMode == com.goat.kharcha.core.AskMode.NEW_PLACES && !t.merchant.isNullOrBlank()
                            toast(app, if (learns) "Filed under $name. ${t.payee} will file itself next time." else "Filed under $name")
                        }
                    }
                    MOOD -> {
                        val m = Mood.values().firstOrNull { it.name == intent.getStringExtra(EXTRA_MOOD) } ?: return@launch
                        Repo.setMood(txId, m)
                        Prompts.cancel(app, txId)
                        toast(app, "Marked as ${m.label.lowercase()}")
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun toast(ctx: Context, msg: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
    }
}
