package com.niko.spesaradar.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "spesaradar.db", null, 3) {

    companion object {
        @Volatile private var instance: Db? = null
        fun get(ctx: Context): Db = instance ?: synchronized(this) {
            instance ?: Db(ctx.applicationContext).also { instance = it }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE watch(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                query TEXT NOT NULL,
                threshold REAL,
                created INTEGER NOT NULL,
                last_refresh INTEGER NOT NULL DEFAULT 0,
                last_error TEXT)"""
        )
        db.execSQL(
            """CREATE TABLE offer(
                watch_id INTEGER NOT NULL,
                okey TEXT NOT NULL,
                source TEXT NOT NULL,
                store TEXT,
                title TEXT NOT NULL,
                price REAL NOT NULL,
                old_price REAL,
                unit_price REAL,
                unit TEXT,
                qty_text TEXT,
                validity TEXT,
                url TEXT,
                sponsored INTEGER NOT NULL DEFAULT 0,
                distance REAL,
                image TEXT,
                end_date TEXT,
                first_seen INTEGER NOT NULL,
                last_seen INTEGER NOT NULL,
                active INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY(watch_id, okey))"""
        )
        db.execSQL(
            """CREATE TABLE history(
                watch_id INTEGER NOT NULL,
                okey TEXT NOT NULL,
                day TEXT NOT NULL,
                price REAL NOT NULL,
                unit_price REAL,
                unit TEXT,
                PRIMARY KEY(watch_id, okey, day))"""
        )
        createFlyerTables(db)
        db.execSQL(
            """CREATE TABLE fetch_log(
                source TEXT PRIMARY KEY,
                at INTEGER NOT NULL,
                url TEXT,
                count INTEGER NOT NULL,
                method TEXT,
                error TEXT,
                html_file TEXT)"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migrazioni senza cancellare lo storico.
        if (oldVersion < 2) db.execSQL("ALTER TABLE offer ADD COLUMN distance REAL")
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE offer ADD COLUMN image TEXT")
            db.execSQL("ALTER TABLE offer ADD COLUMN end_date TEXT")
            createFlyerTables(db)
        }
    }

    private fun createFlyerTables(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS flyer(
                id TEXT PRIMARY KEY,
                chain TEXT NOT NULL,
                slug TEXT NOT NULL,
                start TEXT,
                end_date TEXT,
                logo TEXT,
                item_count INTEGER NOT NULL DEFAULT 0,
                fetched INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS flyer_item(
                flyer_id TEXT NOT NULL,
                item_id TEXT NOT NULL,
                chain TEXT NOT NULL,
                title TEXT NOT NULL,
                brand TEXT,
                price REAL NOT NULL,
                old_price REAL,
                sale TEXT,
                page INTEGER,
                image TEXT,
                url TEXT,
                start TEXT,
                end_date TEXT,
                PRIMARY KEY(flyer_id, item_id))"""
        )
    }

    // ---------- Osservati ----------

    fun watches(): List<Watch> = readableDatabase.rawQuery(
        "SELECT id, query, threshold, last_refresh, last_error FROM watch ORDER BY id DESC", null
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(
                Watch(
                    id = c.getLong(0),
                    query = c.getString(1),
                    threshold = c.doubleOrNull(2),
                    lastRefresh = c.getLong(3),
                    lastError = c.stringOrNull(4)
                )
            )
        }
    }

    fun watch(id: Long): Watch? = watches().firstOrNull { it.id == id }

    fun addWatch(query: String): Long {
        val cv = ContentValues().apply {
            put("query", query.trim())
            put("created", System.currentTimeMillis())
        }
        return writableDatabase.insert("watch", null, cv)
    }

    fun deleteWatch(id: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val a = arrayOf(id.toString())
            db.delete("watch", "id=?", a)
            db.delete("offer", "watch_id=?", a)
            db.delete("history", "watch_id=?", a)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun setThreshold(id: Long, value: Double?) {
        val cv = ContentValues()
        if (value == null) cv.putNull("threshold") else cv.put("threshold", value)
        writableDatabase.update("watch", cv, "id=?", arrayOf(id.toString()))
    }

    fun markRefreshed(id: Long, at: Long, error: String?) {
        val cv = ContentValues().apply {
            put("last_refresh", at)
            if (error == null) putNull("last_error") else put("last_error", error)
        }
        writableDatabase.update("watch", cv, "id=?", arrayOf(id.toString()))
    }

    // ---------- Offerte ----------

    /** Salva il risultato di una fonte: le offerte non piu' trovate diventano non attive. */
    fun saveSourceRun(watchId: Long, source: Source, offers: List<Offer>, now: Long, day: String): Int {
        val db = writableDatabase
        var newCount = 0
        db.beginTransaction()
        try {
            db.execSQL(
                "UPDATE offer SET active=0 WHERE watch_id=? AND source=?",
                arrayOf<Any>(watchId, source.id)
            )
            for (o in offers) {
                val exists = db.rawQuery(
                    "SELECT first_seen FROM offer WHERE watch_id=? AND okey=?",
                    arrayOf(watchId.toString(), o.key)
                ).use { it.moveToFirst() }
                val cv = ContentValues().apply {
                    put("watch_id", watchId)
                    put("okey", o.key)
                    put("source", source.id)
                    put("store", o.store)
                    put("title", o.title)
                    put("price", o.price)
                    putOrNull("old_price", o.oldPrice)
                    putOrNull("unit_price", o.unitPrice)
                    put("unit", o.unit)
                    put("qty_text", o.qtyText)
                    put("validity", o.validity)
                    put("url", o.url)
                    put("sponsored", if (o.sponsored) 1 else 0)
                    putOrNull("distance", o.distanceKm)
                    put("image", o.image)
                    put("end_date", o.endDate)
                    put("last_seen", now)
                    put("active", 1)
                }
                if (exists) {
                    db.update("offer", cv, "watch_id=? AND okey=?", arrayOf(watchId.toString(), o.key))
                } else {
                    cv.put("first_seen", now)
                    db.insert("offer", null, cv)
                    newCount++
                }
                val h = ContentValues().apply {
                    put("watch_id", watchId)
                    put("okey", o.key)
                    put("day", day)
                    put("price", o.price)
                    putOrNull("unit_price", o.unitPrice)
                    put("unit", o.unit)
                }
                db.insertWithOnConflict("history", null, h, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return newCount
    }

    fun offers(watchId: Long, onlyActive: Boolean = true): List<Offer> {
        val sql = "SELECT watch_id, okey, source, store, title, price, old_price, unit_price, unit, qty_text, " +
            "validity, url, sponsored, first_seen, last_seen, active, distance, image, end_date FROM offer WHERE watch_id=?" +
            (if (onlyActive) " AND active=1" else "")
        return readableDatabase.rawQuery(sql, arrayOf(watchId.toString())).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Offer(
                        watchId = c.getLong(0),
                        key = c.getString(1),
                        source = Source.of(c.getString(2)),
                        store = c.stringOrNull(3),
                        title = c.getString(4),
                        price = c.getDouble(5),
                        oldPrice = c.doubleOrNull(6),
                        unitPrice = c.doubleOrNull(7),
                        unit = c.stringOrNull(8),
                        qtyText = c.stringOrNull(9),
                        validity = c.stringOrNull(10),
                        url = c.stringOrNull(11),
                        sponsored = c.getInt(12) == 1,
                        firstSeen = c.getLong(13),
                        lastSeen = c.getLong(14),
                        active = c.getInt(15) == 1,
                        distanceKm = c.doubleOrNull(16),
                        image = c.stringOrNull(17),
                        endDate = c.stringOrNull(18)
                    )
                )
            }
        }
    }

    /** Miglior prezzo unitario per giorno, per una data unita' (L, kg, pz...). */
    fun bestPerDay(watchId: Long, unit: String, sources: Set<Source>? = null): List<TrendPoint> {
        val args = mutableListOf(watchId.toString(), unit)
        var filter = ""
        if (sources != null && sources.isNotEmpty()) {
            filter = " AND o.source IN (" + sources.joinToString(",") { "?" } + ")"
            args.addAll(sources.map { it.id })
        }
        return readableDatabase.rawQuery(
            "SELECT h.day, MIN(h.unit_price) FROM history h JOIN offer o " +
                "ON o.watch_id=h.watch_id AND o.okey=h.okey " +
                "WHERE h.watch_id=? AND h.unit=? AND h.unit_price IS NOT NULL" + filter +
                " GROUP BY h.day ORDER BY h.day",
            args.toTypedArray()
        ).use { c ->
            buildList { while (c.moveToNext()) add(TrendPoint(c.getString(0), c.getDouble(1))) }
        }
    }

    /** Storico di una singola offerta (stesso prodotto, stesso negozio). */
    fun offerHistory(watchId: Long, key: String): List<TrendPoint> = readableDatabase.rawQuery(
        "SELECT day, price FROM history WHERE watch_id=? AND okey=? ORDER BY day",
        arrayOf(watchId.toString(), key)
    ).use { c -> buildList { while (c.moveToNext()) add(TrendPoint(c.getString(0), c.getDouble(1))) } }

    // ---------- Volantini ----------

    fun flyers(): List<Flyer> = readableDatabase.rawQuery(
        "SELECT id, chain, slug, start, end_date, logo, item_count, fetched FROM flyer ORDER BY chain, end_date", null
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(
                Flyer(
                    c.getString(0), c.getString(1), c.getString(2), c.stringOrNull(3), c.stringOrNull(4),
                    c.stringOrNull(5), c.getInt(6), c.getLong(7)
                )
            )
        }
    }

    fun flyerItemCount(id: String): Int = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM flyer_item WHERE flyer_id=?", arrayOf(id)
    ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun saveFlyer(f: Flyer, items: List<FlyerItem>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("flyer_item", "flyer_id=?", arrayOf(f.id))
            for (i in items) {
                val cv = ContentValues().apply {
                    put("flyer_id", i.flyerId)
                    put("item_id", i.itemId)
                    put("chain", i.chain)
                    put("title", i.title)
                    put("brand", i.brand)
                    put("price", i.price)
                    putOrNull("old_price", i.oldPrice)
                    put("sale", i.sale)
                    if (i.page == null) putNull("page") else put("page", i.page)
                    put("image", i.image)
                    put("url", i.url)
                    put("start", i.start)
                    put("end_date", i.end)
                }
                db.insertWithOnConflict("flyer_item", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            val cv = ContentValues().apply {
                put("id", f.id)
                put("chain", f.chain)
                put("slug", f.slug)
                put("start", f.start)
                put("end_date", f.end)
                put("logo", f.logo)
                put("item_count", items.size)
                put("fetched", f.fetched)
            }
            db.insertWithOnConflict("flyer", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Toglie i volantini scaduti (data di fine prima di oggi). */
    fun deleteExpiredFlyers(today: String) {
        val db = writableDatabase
        db.execSQL("DELETE FROM flyer_item WHERE end_date IS NOT NULL AND end_date < ?", arrayOf(today))
        db.execSQL("DELETE FROM flyer WHERE end_date IS NOT NULL AND end_date < ?", arrayOf(today))
    }

    fun flyerItems(flyerId: String? = null, chain: String? = null): List<FlyerItem> {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (flyerId != null) { where += "flyer_id=?"; args += flyerId }
        if (chain != null) { where += "chain=?"; args += chain }
        val sql = "SELECT flyer_id, item_id, chain, title, brand, price, old_price, sale, page, image, url, start, end_date " +
            "FROM flyer_item" + (if (where.isEmpty()) "" else " WHERE " + where.joinToString(" AND ")) +
            " ORDER BY page, title"
        return readableDatabase.rawQuery(sql, args.toTypedArray()).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    FlyerItem(
                        c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.stringOrNull(4),
                        c.getDouble(5), c.doubleOrNull(6), c.stringOrNull(7),
                        if (c.isNull(8)) null else c.getInt(8),
                        c.stringOrNull(9), c.stringOrNull(10), c.stringOrNull(11), c.stringOrNull(12)
                    )
                )
            }
        }
    }

    // ---------- Diagnostica ----------

    fun saveLog(log: FetchLog) {
        val cv = ContentValues().apply {
            put("source", log.source)
            put("at", log.at)
            put("url", log.url)
            put("count", log.count)
            put("method", log.method)
            put("error", log.error)
            put("html_file", log.htmlFile)
        }
        writableDatabase.insertWithOnConflict("fetch_log", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun logs(): List<FetchLog> = readableDatabase.rawQuery(
        "SELECT source, at, url, count, method, error, html_file FROM fetch_log ORDER BY source", null
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(
                FetchLog(
                    c.getString(0), c.getLong(1), c.stringOrNull(2), c.getInt(3),
                    c.stringOrNull(4), c.stringOrNull(5), c.stringOrNull(6)
                )
            )
        }
    }
}

private fun Cursor.stringOrNull(i: Int): String? = if (isNull(i)) null else getString(i)
private fun Cursor.doubleOrNull(i: Int): Double? = if (isNull(i)) null else getDouble(i)
private fun ContentValues.putOrNull(k: String, v: Double?) {
    if (v == null) putNull(k) else put(k, v)
}
