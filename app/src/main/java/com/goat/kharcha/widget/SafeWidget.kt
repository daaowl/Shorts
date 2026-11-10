package com.goat.kharcha.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.goat.kharcha.MainActivity
import com.goat.kharcha.R
import com.goat.kharcha.core.Money
import com.goat.kharcha.core.Stats
import com.goat.kharcha.data.Repo
import kotlinx.coroutines.launch

/** Home screen widget: today's safe-to-spend number, what's left, and how many payments need a reason. */
class SafeWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Repo.init(context.applicationContext)
        val pending = goAsync()
        Repo.scope.launch {
            try {
                Repo.awaitLoaded()
                refresh(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun refresh(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx) ?: return
            val ids = try {
                manager.getAppWidgetIds(ComponentName(ctx, SafeWidget::class.java))
            } catch (_: Throwable) {
                return
            }
            if (ids.isEmpty()) return
            val s = Repo.settings.value
            val o = Stats.overview(Repo.tx.value, s, Repo.today())
            val v = RemoteViews(ctx.packageName, R.layout.widget_safe)

            if (!s.onboarded) {
                v.setTextViewText(R.id.w_label, "KHARCHA")
                v.setTextViewText(R.id.w_amount, "Set up")
                v.setTextViewText(R.id.w_sub, "Open the app to start tracking")
            } else if (o.hasBudget) {
                val over = o.safeToday < 0
                v.setTextViewText(R.id.w_label, if (over) "OVER FOR TODAY" else "SAFE TO SPEND TODAY")
                v.setTextViewText(R.id.w_amount, Money.round(if (over) -o.safeToday else o.safeToday))
                v.setTextColor(R.id.w_amount, if (over) 0xFFF57D80.toInt() else 0xFFE9EDF3.toInt())
                val left = if (o.left >= 0) "${Money.round(o.left)} left" else "${Money.round(-o.left)} over"
                v.setTextViewText(R.id.w_sub, left + " · " + o.daysLeft + (if (o.daysLeft == 1) " day" else " days"))
            } else {
                v.setTextViewText(R.id.w_label, "SPENT THIS CYCLE")
                v.setTextViewText(R.id.w_amount, Money.round(o.spent))
                v.setTextColor(R.id.w_amount, 0xFFE9EDF3.toInt())
                v.setTextViewText(R.id.w_sub, "Today ${Money.round(o.spentToday)}")
            }
            if (o.pending > 0) {
                v.setTextViewText(R.id.w_badge, "${o.pending} to tag")
                v.setViewVisibility(R.id.w_badge, android.view.View.VISIBLE)
            } else {
                v.setViewVisibility(R.id.w_badge, android.view.View.GONE)
            }

            val open = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (o.pending > 0) putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_QUEUE)
            }
            v.setOnClickPendingIntent(
                R.id.w_root,
                PendingIntent.getActivity(ctx, 41, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
            ids.forEach { manager.updateAppWidget(it, v) }
        }
    }
}
