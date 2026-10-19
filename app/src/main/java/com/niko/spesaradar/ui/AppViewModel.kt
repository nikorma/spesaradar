package com.niko.spesaradar.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.niko.spesaradar.data.Db
import com.niko.spesaradar.data.FetchLog
import com.niko.spesaradar.data.Offer
import com.niko.spesaradar.data.Settings
import com.niko.spesaradar.data.Source
import com.niko.spesaradar.data.TrendPoint
import com.niko.spesaradar.data.Watch
import com.niko.spesaradar.data.WatchSummary
import com.niko.spesaradar.fetch.RefreshEngine
import com.niko.spesaradar.work.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

sealed class Screen {
    data object Home : Screen()
    data class Offers(val watchId: Long) : Screen()
    data class Trend(val watchId: Long, val offerKey: String? = null) : Screen()
    data object SettingsScreen : Screen()
    data object Diagnostics : Screen()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val db = Db.get(app)
    val settings = Settings(app)
    private val engine = RefreshEngine(app)

    val summaries = mutableStateListOf<WatchSummary>()
    var refreshing by mutableStateOf(false)
        private set
    var progress by mutableStateOf("")
        private set
    var message by mutableStateOf<String?>(null)

    private val backStack = ArrayDeque<Screen>()
    var screen by mutableStateOf<Screen>(Screen.Home)
        private set

    init {
        reload()
    }

    // ---------- Navigazione ----------

    fun open(s: Screen) {
        backStack.addLast(screen)
        screen = s
    }

    /** @return false se siamo gia' alla schermata iniziale. */
    fun back(): Boolean {
        val prev = backStack.removeLastOrNull() ?: return false
        screen = prev
        reload()
        return true
    }

    fun openFromNotification(watchId: Long) {
        backStack.clear()
        screen = Screen.Home
        open(Screen.Offers(watchId))
    }

    // ---------- Dati ----------

    fun reload() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { db.watches().map { summarize(it) } }
            summaries.clear()
            summaries.addAll(list)
        }
    }

    private fun summarize(w: Watch): WatchSummary {
        val offers = db.offers(w.id, onlyActive = true)
        val unit = mainUnit(offers)
        val best = offers.filter { it.unit == unit && it.unitPrice != null }.minByOrNull { it.unitPrice!! }
        var low = false
        val bp = best?.unitPrice
        if (unit != null && bp != null) {
            val today = LocalDate.now().toString()
            val prev = db.bestPerDay(w.id, unit).filter { it.day != today }.minOfOrNull { it.value }
            low = prev != null && bp < prev - 0.0001
        }
        return WatchSummary(w, offers.size, best, low)
    }

    fun watch(id: Long): Watch? = summaries.firstOrNull { it.watch.id == id }?.watch ?: db.watch(id)

    fun offers(id: Long): List<Offer> = db.offers(id, onlyActive = true)

    fun trend(id: Long, unit: String, sources: Set<Source>?): List<TrendPoint> = db.bestPerDay(id, unit, sources)

    fun offerHistory(id: Long, key: String): List<TrendPoint> = db.offerHistory(id, key)

    fun logs(): List<FetchLog> = db.logs()

    fun addWatch(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) { db.addWatch(q) }
            reload()
            refreshOne(id)
        }
    }

    fun deleteWatch(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.deleteWatch(id) }
            backStack.clear()
            screen = Screen.Home
            reload()
        }
    }

    fun setThreshold(id: Long, value: Double?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.setThreshold(id, value) }
            reload()
            message = if (value == null) "Soglia tolta" else "Soglia salvata"
        }
    }

    fun refreshAll() {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            try {
                val out = engine.refreshAll { p -> progress = p }
                Notifier.notifyOutcomes(getApplication<Application>(), out)
                val errs = out.count { it.errors.isNotEmpty() }
                message = if (errs == 0) "Aggiornato" else "Aggiornato, con problemi su $errs prodotti (vedi Diagnostica)"
            } catch (e: Exception) {
                message = "Errore: ${e.message}"
            } finally {
                refreshing = false
                progress = ""
                reload()
            }
        }
    }

    fun refreshOne(id: Long) {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            try {
                val out = engine.refreshWatch(id) { p -> progress = p }
                message = when {
                    out == null -> null
                    out.errors.isEmpty() -> "Aggiornato"
                    else -> out.errors.joinToString("\n")
                }
            } catch (e: Exception) {
                message = "Errore: ${e.message}"
            } finally {
                refreshing = false
                progress = ""
                reload()
            }
        }
    }

    companion object {
        fun mainUnit(offers: List<Offer>): String? =
            offers.mapNotNull { it.unit }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }
}
