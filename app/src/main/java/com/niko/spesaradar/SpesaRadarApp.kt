package com.niko.spesaradar

import android.app.Application
import com.niko.spesaradar.work.Notifier
import com.niko.spesaradar.work.RefreshWorker

class SpesaRadarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)
        RefreshWorker.schedule(this)
    }
}
