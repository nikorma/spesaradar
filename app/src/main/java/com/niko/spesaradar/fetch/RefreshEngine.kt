package com.niko.spesaradar.fetch

import android.content.Context
import com.niko.spesaradar.data.ChainSites
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
            Source.SITES -> ""
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
            val cfg = cfgFor(source.id, w.query)

            if (source == Source.SITES) {
                // Un giro su ogni sito di catena; offerte salvate insieme, diagnostica separata.
                val all = mutableListOf<Offer>()
                var anyOk = false
                for (site in ChainSites.ALL) {
                    progress("${w.query} · ${site.name}")
                    val u = site.urlFor(w.query)
                    val r = fetcher.fetch(u, cfgFor("site", w.query), timeoutMs = 45_000)
                    val offers = pick(w, r).map { toOffer(w.id, source, it.copy(store = site.name), now) }
                    withContext(Dispatchers.IO) { log("site:" + site.id, now, u, r, offers.size) }
                    if (r.error == null && !r.blocked) anyOk = true
                    all += offers
                    delay(1_000)
                }
                withContext(Dispatchers.IO) {
                    if (anyOk) newOffers += db.saveSourceRun(w.id, source, all.distinctBy { it.key }, now, day)
                    else errors += "Siti catene: nessun sito raggiungibile"
                }
                continue
            }

            val (url, res) = if (source == Source.PROMOQUI) fetchPromoqui(w, cfg, progress)
                else urlFor(source, w.query, settings.city).let { u -> u to fetcher.fetch(u, cfg) }
            val offers = pick(w, res).map { toOffer(w.id, source, it, now) }

            withContext(Dispatchers.IO) {
                log(source.id, now, url, res, offers.size)
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

    /**
     * PromoQui ha pagine solo per le ricerche "note" (es. detersivo-piatti, chilly).
     * Prova la ricerca intera, poi la marca o le parole singole, e si ferma
     * alla prima pagina che contiene offerte pertinenti.
     */
    private suspend fun fetchPromoqui(w: Watch, cfg: String, progress: (String) -> Unit): Pair<String, FetchResult> {
        val tried = mutableListOf<String>()
        var first: Pair<String, FetchResult>? = null
        for (slug in Text.slugCandidates(w.query)) {
            val u = "https://www.promoqui.it/offerte/$slug"
            progress("${w.query} · Volantini · $slug")
            val r = fetcher.fetch(u, cfg)
            val rel = r.items.count { Text.relevant(w.query, it.title) }
            tried += "$slug=$rel/${r.items.size}"
            if (first == null) first = u to r
            if (r.blocked || rel > 0) return u to r.withNote(tried)
        }
        val f = first!!
        // Nessuna pagina utile: non e' un errore di rete, solo zero offerte.
        return f.first to f.second.copy(error = null).withNote(tried)
    }

    private fun cfgFor(source: String, query: String) =
        "{\"source\":\"$source\",\"query\":${Chains.jsonStr(query)},\"chains\":${Chains.toJson()}}"

    private fun pick(w: Watch, r: FetchResult): List<RawItem> = r.items
        .filter { Text.relevant(w.query, it.title) }
        .filter { !(settings.hideSponsored && it.sponsored) }
        .take(MAX_PER_SOURCE)

    private fun log(id: String, now: Long, url: String, r: FetchResult, kept: Int) {
        db.saveLog(
            FetchLog(
                source = id,
                at = now,
                url = r.finalUrl ?: url,
                count = r.items.size,
                method = (r.method ?: "—") + (if (kept != r.items.size) " · $kept pertinenti" else ""),
                error = if (r.blocked) "Il sito ha chiesto una verifica anti-robot" else r.error,
                htmlFile = r.html?.let { saveHtml(id, it) }
            )
        )
    }

    private fun FetchResult.withNote(tried: List<String>) =
        copy(method = (method ?: "—") + " · pagine " + tried.joinToString(", "))

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
            distanceKm = r.distanceKm,
            firstSeen = now,
            lastSeen = now,
            active = true
        )
    }

    private fun saveHtml(id: String, html: String): String? = try {
        val dir = File(ctx.cacheDir, "diagnostica").apply { mkdirs() }
        val f = File(dir, id.replace(':', '_') + ".html")
        f.writeText(html)
        f.name
    } catch (e: Exception) {
        null
    }
}
