package com.niko.spesaradar.data

import java.util.Locale
import kotlin.math.roundToInt

/** Quantita' normalizzata: amount espresso in L, kg, pz o lav (lavaggi). */
data class Qty(val amount: Double, val unit: String, val text: String)

/** Prezzo per unita' (€/L, €/kg, €/pz, €/lav). */
data class UnitPrice(val value: Double, val unit: String)

object Units {

    private const val NUM = "(\\d{1,5}(?:[.,]\\d{1,3})?)"
    private const val MEAS = "(ml|cl|lt|litri|litro|l|grammi|gr|g|kg|chili)"

    private val MULTI_A = Regex("(?i)(?<![\\d.,])(\\d{1,3})\\s*[x×]\\s*$NUM\\s*$MEAS(?![a-zà-ù])")
    private val MULTI_B = Regex("(?i)$NUM\\s*$MEAS\\s*[x×]\\s*(\\d{1,3})(?![\\d.,])")
    private val SINGLE = Regex("(?i)(?<![\\d.,])$NUM\\s*$MEAS(?![a-zà-ù])")
    private val PIECES = Regex(
        "(?i)(?<![\\d.,])(\\d{1,4})\\s*(pz|pezzi|capsule|caps|cialde|tabs|tabs|pastiglie|caps\\.|rotoli|bustine|uova|confezioni|lavaggi|lav\\.?|misurini)(?![a-zà-ù])"
    )
    private val UNIT_PRICE = Regex(
        "(?i)(\\d{1,4}(?:[.,]\\d{1,3})?)\\s*€\\s*/\\s*(100\\s*ml|100\\s*g|litro|litri|lt|l|kg|ml|g|unit[aà]|pz|pezzo|lavaggio|lavaggi|carica|cariche)|€\\s*(\\d{1,4}(?:[.,]\\d{1,3})?)\\s*/\\s*(100\\s*ml|100\\s*g|litro|litri|lt|l|kg|ml|g|unit[aà]|pz|pezzo|lavaggio|lavaggi)"
    )

    fun toNum(s: String): Double? = s.replace(" ", "").replace(",", ".").toDoubleOrNull()

    /** Converte valore+misura in L o kg. */
    private fun measure(v: Double, m: String): Pair<Double, String>? = when (m.lowercase(Locale.ROOT)) {
        "ml" -> v / 1000.0 to "l"
        "cl" -> v / 100.0 to "l"
        "l", "lt", "litri", "litro" -> v to "l"
        "g", "gr", "grammi" -> v / 1000.0 to "kg"
        "kg", "chili" -> v to "kg"
        else -> null
    }

    /** Ricava il formato dal nome prodotto: "3x930 ml", "Svelto 1 L", "500 g", "20 capsule". */
    fun parseQty(title: String): Qty? {
        MULTI_A.find(title)?.let { m ->
            val n = m.groupValues[1].toIntOrNull() ?: return@let
            val v = toNum(m.groupValues[2]) ?: return@let
            val conv = measure(v, m.groupValues[3]) ?: return@let
            if (n in 1..99 && conv.first > 0) return Qty(n * conv.first, conv.second, m.value.trim())
        }
        MULTI_B.find(title)?.let { m ->
            val v = toNum(m.groupValues[1]) ?: return@let
            val conv = measure(v, m.groupValues[2]) ?: return@let
            val n = m.groupValues[3].toIntOrNull() ?: return@let
            if (n in 1..99 && conv.first > 0) return Qty(n * conv.first, conv.second, m.value.trim())
        }
        SINGLE.find(title)?.let { m ->
            val v = toNum(m.groupValues[1]) ?: return@let
            val conv = measure(v, m.groupValues[2]) ?: return@let
            if (conv.first > 0) return Qty(conv.first, conv.second, m.value.trim())
        }
        PIECES.find(title)?.let { m ->
            val n = m.groupValues[1].toIntOrNull() ?: return@let
            val w = m.groupValues[2].lowercase(Locale.ROOT)
            val unit = if (w.startsWith("lav")) "lav" else "pz"
            if (n > 0) return Qty(n.toDouble(), unit, m.value.trim())
        }
        return null
    }

    /** Legge un prezzo unitario gia' scritto dal sito, es. "2,14 €/l" o "€ 0,33/unità". */
    fun parseUnitText(text: String?): UnitPrice? {
        if (text.isNullOrBlank()) return null
        val m = UNIT_PRICE.find(text) ?: return null
        val v = toNum(m.groupValues[1].ifEmpty { m.groupValues[3] }) ?: return null
        val u = m.groupValues[2].ifEmpty { m.groupValues[4] }.lowercase(Locale.ROOT).replace(" ", "")
        return when (u) {
            "l", "lt", "litro", "litri" -> UnitPrice(v, "l")
            "kg" -> UnitPrice(v, "kg")
            "ml" -> UnitPrice(v * 1000, "l")
            "g" -> UnitPrice(v * 1000, "kg")
            "100ml" -> UnitPrice(v * 10, "l")
            "100g" -> UnitPrice(v * 10, "kg")
            "lavaggio", "lavaggi", "carica", "cariche" -> UnitPrice(v, "lav")
            else -> UnitPrice(v, "pz")
        }
    }

    /** Prezzo unitario: prima quello indicato dal sito, altrimenti calcolato dal formato. */
    fun unitPrice(price: Double, title: String, unitText: String?): Pair<UnitPrice?, Qty?> {
        val qty = parseQty(title)
        val fromSite = parseUnitText(unitText)
        if (fromSite != null) return fromSite to qty
        if (qty != null && qty.amount > 0) return UnitPrice(price / qty.amount, qty.unit) to qty
        return null to qty
    }

    fun unitLabel(u: String?): String = when (u) {
        "l" -> "L"
        "kg" -> "kg"
        "lav" -> "lavaggio"
        "pz" -> "pz"
        else -> ""
    }

    fun euro(v: Double?): String {
        if (v == null) return "–"
        val cents = (v * 100).roundToInt()
        return String.format(Locale.ITALY, "%d,%02d €", cents / 100, cents % 100)
    }

    fun perUnit(v: Double?, unit: String?): String =
        if (v == null || unit == null) "" else euro(v) + "/" + unitLabel(unit)
}
