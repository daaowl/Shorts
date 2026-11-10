package com.goat.kharcha.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.goat.kharcha.core.Cycle
import com.goat.kharcha.core.Flow
import com.goat.kharcha.core.Money
import com.goat.kharcha.core.Stats
import com.goat.kharcha.data.Repo
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Sends a one-line summary of the day around 9 pm. */
class RecapWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Repo.init(applicationContext)
        Repo.awaitLoaded()
        val s = Repo.settings.value
        if (!s.recap || !s.onboarded) return Result.success()
        val today = Repo.today()
        val txs = Repo.tx.value
        val todays = txs.filter { it.flow == Flow.OUT && Cycle.date(it.time) == today }
        val o = Stats.overview(txs, s, today)
        if (todays.isEmpty() && o.pending == 0) return Result.success()

        val title = if (todays.isEmpty()) "No spending today" else "Today: ${Money.round(todays.sumOf { it.amount })} across ${todays.size} payment${if (todays.size == 1) "" else "s"}"
        val parts = ArrayList<String>()
        if (o.hasBudget) {
            val tomorrow = (o.budget - o.spent - o.reserved) / maxOf(1, o.daysLeft - 1)
            parts += if (tomorrow >= 0) "Tomorrow you can spend ${Money.round(tomorrow)} and stay on budget." else "You're ${Money.round(-(o.budget - o.spent))} over this cycle's budget."
        }
        if (o.pending > 0) parts += "${o.pending} payment${if (o.pending == 1) " still needs" else "s still need"} a reason."
        Prompts.recap(applicationContext, title, parts.joinToString(" ").ifBlank { "Open Kharcha to see where it went." }, o.pending)
        return Result.success()
    }

    companion object {
        private const val NAME = "nightly-recap"

        fun schedule(ctx: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(ctx)
            if (!enabled) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val now = LocalDateTime.now()
            var at = now.withHour(21).withMinute(0).withSecond(0).withNano(0)
            if (!at.isAfter(now)) at = at.plusDays(1)
            val delay = Duration.between(now, at).toMinutes()
            val req = PeriodicWorkRequestBuilder<RecapWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
