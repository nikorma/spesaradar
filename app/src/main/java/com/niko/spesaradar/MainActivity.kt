package com.niko.spesaradar

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.niko.spesaradar.ui.AppRoot
import com.niko.spesaradar.ui.AppViewModel
import com.niko.spesaradar.ui.SpesaRadarTheme
import com.niko.spesaradar.work.Notifier

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent {
            SpesaRadarTheme {
                AppRoot(vm, onExit = { finish() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val id = intent?.getLongExtra(Notifier.EXTRA_WATCH, -1L) ?: -1L
        if (id > 0) {
            vm.openFromNotification(id)
            intent?.removeExtra(Notifier.EXTRA_WATCH)
        }
    }
}
