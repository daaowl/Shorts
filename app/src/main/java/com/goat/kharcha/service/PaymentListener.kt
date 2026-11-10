package com.goat.kharcha.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.BundleCompat
import com.goat.kharcha.core.AppKind
import com.goat.kharcha.core.Flow
import com.goat.kharcha.core.Parser
import com.goat.kharcha.core.Tx
import com.goat.kharcha.data.Repo
import kotlinx.coroutines.launch

/**
 * Reads every notification the phone shows and keeps only the ones that describe money moving.
 * Nothing leaves the phone: the text is parsed here and stored in the app's own database.
 */
class PaymentListener : NotificationListenerService() {

    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            handle(sbn)
        } catch (_: Throwable) {
            // A malformed notification from another app must never take the listener down.
        }
    }

    private fun handle(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName || pkg == "android" || pkg == "com.android.systemui") return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (sbn.isOngoing) return

        val label = appLabel(this, pkg, n)
        val texts = extractTexts(n)
        if (texts.isEmpty()) return
        val time = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis()
        Detector.handle(this, pkg, label, texts, time)
    }

    companion object {
        fun isEnabled(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        fun rebind(context: Context) {
            if (!isEnabled(context)) return
            try {
                NotificationListenerService.requestRebind(ComponentName(context, PaymentListener::class.java))
            } catch (_: Throwable) {
            }
        }

        fun appLabel(context: Context, pkg: String, n: Notification? = null): String {
            val pm = context.packageManager
            val fromExtras = n?.extras?.let { BundleCompat.getParcelable(it, "android.appInfo", ApplicationInfo::class.java) }
            fromExtras?.let { info ->
                runCatching { pm.getApplicationLabel(info).toString() }.getOrNull()?.takeIf { it.isNotBlank() && it != pkg }?.let { return it }
            }
            return try {
                pm.getApplicationLabel(legacyInfo(pm, pkg)).toString()
            } catch (_: Throwable) {
                pkg.substringAfterLast('.').replaceFirstChar { it.uppercaseChar() }
            }
        }

        @Suppress("DEPRECATION")
        private fun legacyInfo(pm: PackageManager, pkg: String): ApplicationInfo =
            if (Build.VERSION.SDK_INT >= 33) pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
            else pm.getApplicationInfo(pkg, 0)

        /** Title plus the newest message. For chat-style SMS apps only the latest message is new. */
        fun extractTexts(n: Notification): List<Pair<String?, String>> {
            val ex = n.extras ?: return emptyList()
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?: ex.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
            val out = ArrayList<Pair<String?, String>>()
            val style = runCatching { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n) }.getOrNull()
            val last = style?.messages?.lastOrNull()
            if (last != null) {
                val sender = last.person?.name?.toString() ?: style.conversationTitle?.toString() ?: title
                last.text?.toString()?.takeIf { it.isNotBlank() }?.let { out += sender to it }
                return out
            }
            val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val main = big?.takeIf { it.isNotBlank() } ?: text
            if (!main.isNullOrBlank()) out += title to main
            ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { line ->
                val s = line?.toString()
                if (!s.isNullOrBlank() && s != main) out += title to s
            }
            if (out.isEmpty() && !title.isNullOrBlank()) out += null to title
            return out
        }
    }
}

/** Shared by the listener and the in-app test button so both follow the exact same path. */
object Detector {
    fun handle(context: Context, pkg: String, label: String, texts: List<Pair<String?, String>>, time: Long) {
        val app = context.applicationContext
        Repo.scope.launch {
            Repo.awaitLoaded()
            val s = Repo.settings.value
            if (!s.onboarded || Repo.isMuted(pkg)) return@launch
            val kind = Parser.kind(pkg, label)
            var recorded = false
            for ((title, body) in texts) {
                val p = Parser.parse(pkg, label, title, body, kind, s.scope) ?: continue
                if (p.flow == Flow.IN && !s.detectIncome) continue
                val raw = listOfNotNull(title?.takeIf { it.isNotBlank() }, body).joinToString(": ")
                val tx = Tx(
                    amount = p.amount, flow = p.flow, merchant = p.merchant, time = time,
                    source = p.source, packages = pkg, raw = raw,
                )
                val result = Repo.ingest(tx, pkg, kind == AppKind.MERCHANT) ?: continue
                if (!recorded) {
                    Repo.recordApp(pkg, label, kind.name, time)
                    recorded = true
                }
                Prompts.onDetected(app, result)
            }
        }
    }
}
