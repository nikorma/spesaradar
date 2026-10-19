package com.niko.spesaradar.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.niko.spesaradar.MainActivity
import com.niko.spesaradar.R
import com.niko.spesaradar.data.Settings
import com.niko.spesaradar.data.Units
import com.niko.spesaradar.fetch.WatchOutcome

object Notifier {
    const val CHANNEL = "offerte"
    const val EXTRA_WATCH = "watch_id"

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "Offerte trovate", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Minimi storici, prezzi sotto soglia e nuove offerte"
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notifyOutcomes(ctx: Context, outcomes: List<WatchOutcome>) {
        if (!canNotify(ctx)) return
        val settings = Settings(ctx)
        for (o in outcomes) {
            val best = o.bestOffer
            val bestTxt = if (best != null)
                "${Units.perUnit(o.bestValue, o.bestUnit)} · ${best.store ?: best.source.label} · ${best.title}"
            else null
            val title = when {
                o.isHistoricLow -> "Minimo storico: ${o.watch.query}"
                o.underThreshold -> "Sotto la tua soglia: ${o.watch.query}"
                o.newOffers > 0 && settings.notifyNew ->
                    if (o.newOffers == 1) "1 nuova offerta: ${o.watch.query}" else "${o.newOffers} nuove offerte: ${o.watch.query}"
                else -> null
            } ?: continue
            send(ctx, o.watch.id, title, bestTxt ?: "Apri per vedere le offerte")
        }
    }

    @Suppress("MissingPermission")
    private fun send(ctx: Context, watchId: Long, title: String, text: String) {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_WATCH, watchId)
        }
        val pi = PendingIntent.getActivity(
            ctx, watchId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(watchId.toInt(), n)
    }
}
