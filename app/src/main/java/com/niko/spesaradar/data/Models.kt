package com.niko.spesaradar.data

import java.text.Normalizer
import java.util.Locale

enum class Source(val id: String, val label: String, val online: Boolean) {
    FLYERS("flyers", "Volantini catene", false),
    PROMOQUI("promoqui", "Altri volantini", false),
    SITES("sites", "Siti catene", true),
    AMAZON("amazon", "Amazon", true),
    TROVAPREZZI("trovaprezzi", "Online", true);

    companion object {
        fun of(id: String): Source = entries.firstOrNull { it.id == id }
            ?: if (id.startsWith("site:")) SITES else PROMOQUI

        /** Etichetta per la diagnostica: i siti delle catene hanno id "site:<nome>". */
        fun labelOf(id: String): String = when {
            id.startsWith("site:") -> "Sito " + ChainSites.ALL.firstOrNull { it.id == id.removePrefix("site:") }?.name.orEmpty()
            id.startsWith("flyer:") -> "Volantino " + id.removePrefix("flyer:")
            else -> of(id).label
        }
    }
}

/** Ricerca diretta sui siti delle catene. Gli indirizzi si tarano con la diagnostica. */
data class ChainSite(val id: String, val name: String, val url: String) {
    fun urlFor(query: String): String = url.replace("{q}", java.net.URLEncoder.encode(query.trim(), "UTF-8"))
        .replace("{slug}", Text.slug(query))
}

object ChainSites {
    val ALL = listOf(
        ChainSite("carrefour", "Carrefour", "https://www.carrefour.it/search?q={q}"),
        ChainSite("esselunga", "Esselunga", "https://spesaonline.esselunga.it/commerce/nav/supermercato/store/ricerca/{q}"),
        ChainSite("conad", "Conad", "https://spesaonline.conad.it/search?q={q}"),
        ChainSite("coop", "Coop", "https://www.easycoop.com/search?q={q}"),
        ChainSite("lidl", "Lidl", "https://www.lidl.it/q/search?q={q}"),
        ChainSite("eurospin", "Eurospin", "https://www.eurospin.it/?s={q}")
    )
}

/**
 * Una catena: nome, nomi con cui compare sui siti, slug delle sue pagine volantino su PromoQui
 * e colore per la grafica.
 */
data class Chain(val name: String, val keys: List<String>, val slugs: List<String> = emptyList(), val color: Long = 0xFF0F6E56)

object Chains {
    /** Le catene di Niko. Le chiavi sono i nomi con cui compaiono sui siti. */
    val MINE = listOf(
        Chain("Despar", listOf("despar", "eurospar", "interspar"),
            listOf("despar", "eurospar", "interspar"), 0xFFD7262F),
        Chain("Lidl", listOf("lidl"), listOf("lidl"), 0xFF0050AA),
        Chain("Eurospin", listOf("eurospin"), listOf("eurospin"), 0xFF003B8E),
        Chain("Esselunga", listOf("esselunga", "esselunga superstore", "laesse"), listOf("esselunga"), 0xFF1E5AA8),
        Chain("Carrefour", listOf("carrefour", "carrefour express", "carrefour market", "carrefour iper", "carrefour ipermercati"),
            listOf("carrefour-market", "carrefour-ipermercati", "carrefour-express", "carrefour"), 0xFF1C5BA8),
        Chain("Alì", listOf("alì", "alìper", "aliper", "alì supermercati", "ali supermercati"),
            listOf("ali", "ali-supermercati", "aliper"), 0xFFE30613),
        Chain("Coop", listOf("coop", "ipercoop", "coop alleanza", "coop alleanza 3.0", "incoop"),
            listOf("coop", "ipercoop", "coop-alleanza-3-0"), 0xFFE2001A),
        Chain("Conad", listOf("conad", "spazio conad", "conad city", "conad superstore", "margherita conad", "sapori & dintorni"),
            listOf("conad", "conad-superstore", "conad-city", "spazio-conad"), 0xFFE30613),
        Chain("MD", listOf("md", "md discount", "md spa"), listOf("md"), 0xFFFFC20E),
        Chain("C+C", listOf("c+c", "c+c cash and carry", "cc cash", "cash and carry", "cash & carry"),
            listOf("c-c-cash-and-carry", "cc-cash-carry", "c-c", "cc"), 0xFF00843D)
    )

    fun isMine(store: String?): Boolean = store != null && MINE.any { it.name.equals(store, ignoreCase = true) }

    fun byName(store: String?): Chain? = MINE.firstOrNull { it.name.equals(store, ignoreCase = true) }

    fun toJson(): String = MINE.joinToString(",", "[", "]") { c ->
        "{\"name\":${jsonStr(c.name)},\"keys\":" + c.keys.joinToString(",", "[", "]") { jsonStr(it) } + "}"
    }

    fun jsonStr(s: String): String = buildString {
        append('"')
        for (ch in s) when (ch) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            else -> append(ch)
        }
        append('"')
    }
}

data class Watch(
    val id: Long,
    val query: String,
    val threshold: Double?,
    val lastRefresh: Long,
    val lastError: String?
)

data class Offer(
    val watchId: Long,
    val key: String,
    val source: Source,
    val store: String?,
    val title: String,
    val price: Double,
    val oldPrice: Double?,
    val unitPrice: Double?,
    val unit: String?,
    val qtyText: String?,
    val validity: String?,
    val url: String?,
    val sponsored: Boolean,
    val distanceKm: Double?,
    val image: String? = null,
    val endDate: String? = null,
    val firstSeen: Long,
    val lastSeen: Long,
    val active: Boolean
)

/** Un volantino in corso di una catena, letto per intero. */
data class Flyer(
    val id: String,
    val chain: String,
    val slug: String,
    val start: String?,
    val end: String?,
    val logo: String?,
    val itemCount: Int,
    val fetched: Long
)

/** Una singola offerta dentro un volantino. */
data class FlyerItem(
    val flyerId: String,
    val itemId: String,
    val chain: String,
    val title: String,
    val brand: String?,
    val price: Double,
    val oldPrice: Double?,
    val sale: String?,
    val page: Int?,
    val image: String?,
    val url: String?,
    val start: String?,
    val end: String?
)

/** Un punto del grafico: giorno (yyyy-MM-dd) e miglior prezzo unitario del giorno. */
data class TrendPoint(val day: String, val value: Double)

data class WatchSummary(
    val watch: Watch,
    val activeCount: Int,
    val best: Offer?,
    val historicLow: Boolean
)

data class FetchLog(
    val source: String,
    val at: Long,
    val url: String?,
    val count: Int,
    val method: String?,
    val error: String?,
    val htmlFile: String?
)

object Text {
    fun norm(s: String): String {
        val n = Normalizer.normalize(s.lowercase(Locale.ITALY), Normalizer.Form.NFD)
        return n.replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9+]+"), " ").trim()
    }

    fun slug(s: String): String = norm(s).replace(' ', '-')

    /** Indirizzi da provare su PromoQui: ricerca intera, ultima parola (spesso la marca), prima parola, prime due. */
    fun slugCandidates(query: String): List<String> {
        val words = norm(query).split(' ').filter { it.length >= 3 }
        val out = LinkedHashSet<String>()
        out += slug(query)
        if (words.size > 1) {
            out += words.last()
            out += words.first()
            out += words.take(2).joinToString("-")
        }
        return out.filter { it.isNotBlank() }.take(4)
    }

    /** Parole significative della ricerca (almeno 3 lettere). */
    fun tokens(s: String): List<String> = norm(s).split(' ').filter { it.length >= 3 }

    /**
     * L'offerta e' pertinente se contiene almeno meta' delle parole cercate.
     * Il confronto usa le prime 5 lettere, cosi' "piatti" trova anche "piatto".
     */
    fun relevant(query: String, title: String): Boolean {
        val q = tokens(query)
        if (q.isEmpty()) return true
        val t = norm(title)
        val hits = q.count { w -> t.contains(w.take(5)) }
        return hits >= (q.size + 1) / 2
    }
}
