package com.niko.spesaradar.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.niko.spesaradar.data.Settings
import com.niko.spesaradar.fetch.RefreshEngine
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = try {
        val outcomes = RefreshEngine(applicationContext).refreshAll()
        Notifier.notifyOutcomes(applicationContext, outcomes)
        Result.success()
    } catch (e: Exception) {
        if (runAttemptCount < 2) Result.retry() else Result.failure()
    }

    companion object {
        private const val NAME = "controllo_giornaliero"

        /** Programma il controllo ogni giorno all'ora scelta (default 7:00). */
        fun schedule(ctx: Context, replace: Boolean = false) {
            val hour = Settings(ctx).hour
            val now = LocalDateTime.now()
            var next = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delayMin = Duration.between(now, next).toMinutes()

            val req = PeriodicWorkRequestBuilder<RefreshWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayMin, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                NAME,
                if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }
    }
}
