package com.niko.spesaradar.fetch

import android.content.Context
import com.niko.spesaradar.data.Chains
import com.niko.spesaradar.data.Db
import com.niko.spesaradar.data.FetchLog
import com.niko.spesaradar.data.Offer
import com.niko.spesaradar.data.Settings
import com.niko.spesaradar.data.Source
import com.niko.spesaradar.data.Text
import com.niko.spesaradar.data.Units
import com.niko.spesaradar.data.Watch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.time.LocalDate

/** Esito dell'aggiornamento di un prodotto osservato, usato per decidere le notifiche. */
data class WatchOutcome(
    val watch: Watch,
    val newOffers: Int,
    val bestValue: Double?,
    val bestUnit: String?,
    val bestOffer: Offer?,
    val previousLow: Double?,
    val errors: List<String>
) {
    val isHistoricLow: Boolean
        get() = bestValue != null && previousLow != null && bestValue < previousLow - 0.0001
    val underThreshold: Boolean
        get() = bestValue != null && watch.threshold != null && bestValue <= watch.threshold
}

class RefreshEngine(private val ctx: Context) {

    companion object {
        /** Un solo aggiornamento alla volta (manuale o automatico). */
        val lock = Mutex()
        private const val MAX_PER_SOURCE = 25
    }

    private val db = Db.get(ctx)
    private val settings = Settings(ctx)
    private val fetcher = WebFetcher(ctx)

    fun urlFor(source: Source, query: String, city: String, national: Boolean = false): String {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        return when (source) {
            Source.PROMOQUI ->
                if (national) "https://www.promoqui.it/offerte/${Text.slug(query)}"
                else "https://www.promoqui.it/$city/offerte/${Text.slug(query)}"
            Source.AMAZON -> "https://www.amazon.it/s?k=$q"
            Source.TROVAPREZZI -> "https://www.trovaprezzi.it/categoria.aspx?libera=$q&id=-1"
        }
    }

    suspend fun refreshAll(progress: (String) -> Unit = {}): List<WatchOutcome> = lock.withLock {
        val out = mutableListOf<WatchOutcome>()
        val list = withContext(Dispatchers.IO) { db.watches() }
        for ((i, w) in list.withIndex()) {
            progress("${i + 1}/${list.size} · ${w.query}")
            out += refreshOne(w, progress)
        }
        out
    }

    suspend fun refreshWatch(id: Long, progress: (String) -> Unit = {}): WatchOutcome? = lock.withLock {
        val w = withContext(Dispatchers.IO) { db.watch(id) } ?: return@withLock null
        refreshOne(w, progress)
    }

    private suspend fun refreshOne(w: Watch, progress: (String) -> Unit): WatchOutcome {
        val now = System.currentTimeMillis()
        val day = LocalDate.now().toString()
        val errors = mutableListOf<String>()
        var newOffers = 0

        // Minimo storico prima di questo aggiornamento (escluso oggi).
        val before = withContext(Dispatchers.IO) { db.offers(w.id, onlyActive = false) }
        val mainUnit = before.mapNotNull { it.unit }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

        for (source in Source.entries) {
            if (!settings.sourceEnabled(source)) continue
            progress("${w.query} · ${source.label}")
            val cfg = "{\"source\":\"${source.id}\",\"chains\":${Chains.toJson()}}"
            var url = urlFor(source, w.query, settings.city)
            var res = fetcher.fetch(url, cfg)
            if (source == Source.PROMOQUI && res.items.isEmpty() && !res.blocked) {
                // Se la pagina della citta' non esiste, prova quella nazionale.
                url = urlFor(source, w.query, settings.city, national = true)
                val res2 = fetcher.fetch(url, cfg)
                if (res2.items.isNotEmpty()) res = res2.copy(method = (res2.method ?: "") + " (nazionale)")
            }

            val offers = res.items
                .filter { Text.relevant(w.query, it.title) }
                .filter { !(settings.hideSponsored && it.sponsored) }
                .take(MAX_PER_SOURCE)
                .map { toOffer(w.id, source, it, now) }

            withContext(Dispatchers.IO) {
                val htmlName = res.html?.let { saveHtml(source, it) }
                db.saveLog(
                    FetchLog(
                        source = source.id,
                        at = now,
                        url = res.finalUrl ?: url,
                        count = res.items.size,
                        method = (res.method ?: "—") +
                            (if (offers.size != res.items.size) " · ${offers.size} pertinenti" else ""),
                        error = when {
                            res.blocked -> "Il sito ha chiesto una verifica anti-robot"
                            else -> res.error
                        },
                        htmlFile = htmlName
                    )
                )
                // Se la fonte ha fallito non tocco le offerte gia' salvate.
                if (res.error == null && !res.blocked) {
                    newOffers += db.saveSourceRun(w.id, source, offers, now, day)
                } else {
                    errors += "${source.label}: ${res.error ?: "bloccato"}"
                }
            }
            delay(1_500)
        }

        val after = withContext(Dispatchers.IO) { db.offers(w.id, onlyActive = true) }
        val unit = after.mapNotNull { it.unit }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: mainUnit
        val best = after.filter { it.unit == unit && it.unitPrice != null }.minByOrNull { it.unitPrice!! }
        val previousLow = if (unit == null) null else withContext(Dispatchers.IO) {
            db.bestPerDay(w.id, unit).filter { it.day != day }.minOfOrNull { it.value }
        }
        withContext(Dispatchers.IO) { db.markRefreshed(w.id, now, errors.joinToString("\n").ifEmpty { null }) }
        return WatchOutcome(w, newOffers, best?.unitPrice, unit, best, previousLow, errors)
    }

    private fun toOffer(watchId: Long, source: Source, r: RawItem, now: Long): Offer {
        val (up, qty) = Units.unitPrice(r.price, r.title, r.unitText)
        val store = when (source) {
            Source.AMAZON -> "Amazon"
            else -> r.store
        }
        val key = source.id + "|" + Text.norm(store ?: "") + "|" + Text.norm(r.title)
        return Offer(
            watchId = watchId,
            key = key,
            source = source,
            store = store,
            title = r.title,
            price = r.price,
            oldPrice = r.oldPrice,
            unitPrice = up?.value,
            unit = up?.unit,
            qtyText = qty?.text,
            validity = r.validity,
            url = r.url,
            sponsored = r.sponsored,
            firstSeen = now,
            lastSeen = now,
            active = true
        )
    }

    private fun saveHtml(source: Source, html: String): String? = try {
        val dir = File(ctx.cacheDir, "diagnostica").apply { mkdirs() }
        val f = File(dir, "${source.id}.html")
        f.writeText(html)
        f.name
    } catch (e: Exception) {
        null
    }
}
