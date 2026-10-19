package com.niko.spesaradar.data

import android.content.Context

class Settings(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Citta' come compare negli indirizzi di PromoQui (es. "bologna", "san-lazzaro-di-savena"). */
    var city: String
        get() = p.getString("city", "bologna") ?: "bologna"
        set(v) = p.edit().putString("city", Text.slug(v).ifEmpty { "bologna" }).apply()

    var onlyMyChains: Boolean
        get() = p.getBoolean("only_my_chains", true)
        set(v) = p.edit().putBoolean("only_my_chains", v).apply()

    var hideSponsored: Boolean
        get() = p.getBoolean("hide_sponsored", true)
        set(v) = p.edit().putBoolean("hide_sponsored", v).apply()

    var notifyNew: Boolean
        get() = p.getBoolean("notify_new", true)
        set(v) = p.edit().putBoolean("notify_new", v).apply()

    /** Ora del controllo automatico (0-23). */
    var hour: Int
        get() = p.getInt("hour", 7)
        set(v) = p.edit().putInt("hour", v.coerceIn(0, 23)).apply()

    fun sourceEnabled(s: Source): Boolean = p.getBoolean("src_" + s.id, true)
    fun setSourceEnabled(s: Source, on: Boolean) = p.edit().putBoolean("src_" + s.id, on).apply()
}
