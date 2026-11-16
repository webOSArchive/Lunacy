package org.webosarchive.lunacy.card

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.webosarchive.lunacy.core.Assets
import org.webosarchive.lunacy.core.Images
import java.io.File
import java.io.InputStream

/** The APK's assets, as LunaCore's [Assets]. */
class AndroidAssets(val manager: AssetManager) : Assets {
    override fun open(path: String): InputStream = manager.open(path)
    override fun list(path: String): Array<String>? = manager.list(path)
}

/** BitmapFactory, as LunaCore's [Images]: what AppRegistry and AppServer did with it before LunaCore. */
object AndroidImages : Images {
    override fun size(input: InputStream): IntArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(input, null, bounds)
        return if (bounds.outWidth > 0) intArrayOf(bounds.outWidth, bounds.outHeight) else null
    }

    override fun jpegThumbnail(file: File, px: Int): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= px) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = px.toFloat() / minOf(bmp.width, bmp.height)
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, Math.round(bmp.width * scale), Math.round(bmp.height * scale), true) else bmp
        val bytes = java.io.ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 80, bytes)
        if (out !== bmp) out.recycle()
        bmp.recycle()
        return bytes.toByteArray()
    }

    /** SystemService's wallpaper thumbnail, as it made it before LunaCore. */
    override fun coverThumbnail(file: File, px: Int): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 2 * px || bounds.outHeight / sample > 2 * px) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = maxOf(px.toFloat() / bmp.width, px.toFloat() / bmp.height)
        val thumb = Bitmap.createScaledBitmap(bmp, Math.round(bmp.width * scale), Math.round(bmp.height * scale), true)
        val bytes = java.io.ByteArrayOutputStream()
        thumb.compress(Bitmap.CompressFormat.JPEG, 80, bytes)
        if (thumb !== bmp) thumb.recycle()
        bmp.recycle()
        return bytes.toByteArray()
    }
}

/** The WebViews' cookie jar, as LunaCore's [org.webosarchive.lunacy.core.Cookies]: native requests and pages share it. */
object AndroidCookies : org.webosarchive.lunacy.core.Cookies {
    override fun get(url: String): String? = android.webkit.CookieManager.getInstance().getCookie(url)
    override fun set(url: String, setCookie: String) { android.webkit.CookieManager.getInstance().setCookie(url, setCookie) }
}

/** android.database.sqlite, as LunaCore's [org.webosarchive.lunacy.core.Sql]: what db8 called directly before LunaCore. */
object AndroidSql : org.webosarchive.lunacy.core.Sql {
    override fun open(file: File?): org.webosarchive.lunacy.core.SqlDb = AndroidSqlDb(
        if (file != null) android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null)
        else android.database.sqlite.SQLiteDatabase.create(null))
}

private class AndroidSqlDb(private val db: android.database.sqlite.SQLiteDatabase) : org.webosarchive.lunacy.core.SqlDb {
    override fun exec(sql: String, vararg args: Any?) {
        if (args.isEmpty()) db.execSQL(sql) else db.execSQL(sql, args)
    }

    override fun <T> query(sql: String, args: Array<String>?, read: (org.webosarchive.lunacy.core.SqlRows) -> T): T =
        db.rawQuery(sql, args).use { c ->
            read(object : org.webosarchive.lunacy.core.SqlRows {
                override fun next() = c.moveToNext()
                override fun getString(column: Int): String? = c.getString(column)
                override fun getLong(column: Int) = c.getLong(column)
            })
        }

    // Non-exclusive, as WebSql began its transactions: two windows of one app share a file. db8,
    // the only other user, has one connection and one thread, so it is the same to it.
    override fun beginTransaction() = db.beginTransactionNonExclusive()
    override fun setTransactionSuccessful() = db.setTransactionSuccessful()
    override fun endTransaction() = db.endTransaction()
    override fun inTransaction() = db.inTransaction()
    override fun close() = db.close()

    // ---- WebSQL's statements, as WebSql ran them itself before LunaCore ----

    override fun select(sql: String, args: List<Any?>): org.webosarchive.lunacy.core.SqlTable {
        // Prepared here (SQLiteQuery compiles as it is made), bound typed in the factory.
        val cursor = try {
            db.rawQueryWithFactory({ _, driver, editTable, q -> bind(q, args); android.database.sqlite.SQLiteCursor(driver, editTable, q) }, sql, null, "")
        } catch (e: Exception) {
            throw org.webosarchive.lunacy.core.SqlError(org.webosarchive.lunacy.core.SqlError.Kind.PREPARE, message(e))
        }
        try {
            cursor.use { c ->
                val columns = c.columnNames.toList()
                val rows = ArrayList<List<Any?>>()
                while (c.moveToNext()) rows += (0 until c.columnCount).map { i ->
                    when (c.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_NULL -> null
                        android.database.Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                        android.database.Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                        android.database.Cursor.FIELD_TYPE_BLOB -> String(c.getBlob(i), Charsets.ISO_8859_1)
                        else -> c.getString(i)
                    }
                }
                return org.webosarchive.lunacy.core.SqlTable(columns, rows)
            }
        } catch (e: Exception) {
            throw org.webosarchive.lunacy.core.SqlError(stepKind(e), message(e))
        }
    }

    override fun update(sql: String, args: List<Any?>): org.webosarchive.lunacy.core.SqlChange {
        val st = try { db.compileStatement(sql) } catch (e: Exception) {
            throw org.webosarchive.lunacy.core.SqlError(org.webosarchive.lunacy.core.SqlError.Kind.PREPARE, message(e))
        }
        val changed = try {
            st.use { bind(it, args); it.executeUpdateDelete() }
        } catch (e: Exception) {
            throw org.webosarchive.lunacy.core.SqlError(stepKind(e), message(e))
        }
        val id = if (changed > 0) android.database.DatabaseUtils.longForQuery(db, "SELECT last_insert_rowid()", null) else null
        return org.webosarchive.lunacy.core.SqlChange(changed, id)
    }

    private fun bind(p: android.database.sqlite.SQLiteProgram, args: List<Any?>) {
        for ((i, v) in args.withIndex()) when (v) {
            null -> p.bindNull(i + 1)
            is Long -> p.bindLong(i + 1, v)
            is Int -> p.bindLong(i + 1, v.toLong())
            is Number -> p.bindDouble(i + 1, v.toDouble())
            else -> p.bindString(i + 1, v.toString())
        }
    }

    private fun stepKind(e: Exception) = when (e) {
        is android.database.sqlite.SQLiteConstraintException -> org.webosarchive.lunacy.core.SqlError.Kind.CONSTRAINT
        is android.database.sqlite.SQLiteFullException -> org.webosarchive.lunacy.core.SqlError.Kind.FULL
        else -> org.webosarchive.lunacy.core.SqlError.Kind.OTHER
    }

    /**
     * SQLite's own message, as the device gave it. Android appends its own "(code 1
     * SQLITE_ERROR): , while compiling: …"; a page read the message against SQLite's text.
     */
    private fun message(e: Exception): String {
        val m = e.message ?: return e.toString()
        return ANDROID_SUFFIX.find(m)?.let { m.substring(0, it.range.first) } ?: m
    }

    private companion object { val ANDROID_SUFFIX = Regex(""" \(code \d+""") }
}

/** WebSQL databases Android's WebView stored itself, before it lost WebSQL: brought into Lunacy's own (WebSql's `migrate`). */
class AndroidWebSqlMigration(private val context: android.content.Context) {
    /**
     * A database the WebView stored while it still had WebSQL: Chromium kept an index,
     * `Databases.db`, mapping an origin (`https_host_0`) and a name to a numbered file in
     * that origin's folder. Copied in whole, version table and all, so an app that kept its
     * data in the WebView's WebSQL finds it after a WebView update takes WebSQL away.
     */
    fun migrate(target: File, host: String, name: String) {
        val data = context.filesDir.parentFile ?: return
        val origin = "https_${host}_0"
        for (dir in listOf("app_webview/Default/databases", "app_webview/databases", "app_websql/databases")) {
            val base = File(data, dir)
            val index = File(base, "Databases.db")
            if (!index.isFile) continue
            try {
                android.database.sqlite.SQLiteDatabase.openDatabase(index.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { idx ->
                    idx.rawQuery("SELECT id FROM Databases WHERE origin = ? AND name = ?", arrayOf(origin, name)).use { c ->
                        if (!c.moveToFirst()) return@use
                        val source = File(base, "$origin/${c.getLong(0)}")
                        if (!source.isFile) return@use
                        source.copyTo(target)
                        org.webosarchive.lunacy.core.Log.i(AppServer.TAG, "websql [$host] migrated \"$name\" from $dir (${source.length()} bytes)")
                        return
                    }
                }
            } catch (e: Exception) {
                org.webosarchive.lunacy.core.Log.w(AppServer.TAG, "websql [$host] migrating \"$name\" from $dir: $e")
            }
        }
    }
}

/** java.text.Collator, which is ICU on Android, as LunaCore's [org.webosarchive.lunacy.core.Collation]. */
object AndroidCollation : org.webosarchive.lunacy.core.Collation {
    override fun comparator(strength: String): Comparator<String> {
        val c = java.text.Collator.getInstance().apply {
            this.strength = when (strength) { "primary" -> java.text.Collator.PRIMARY; "secondary" -> java.text.Collator.SECONDARY; else -> java.text.Collator.TERTIARY }
        }
        return Comparator { a, b -> c.compare(a, b) }
    }
}

/** android.system.Os, as LunaCore's [org.webosarchive.lunacy.core.Links]. */
object AndroidLinks : org.webosarchive.lunacy.core.Links {
    override fun readlink(path: String): String? = runCatching { android.system.Os.readlink(path) }.getOrNull()
    override fun symlink(target: String, path: String) { android.system.Os.symlink(target, path) }
}

/** Android's battery broadcasts, as LunaCore's [org.webosarchive.lunacy.core.BatteryWatch]: what ActivityRequirements watched itself before LunaCore. */
class AndroidBattery(private val context: android.content.Context) : org.webosarchive.lunacy.core.BatteryWatch {
    private var receiver: android.content.BroadcastReceiver? = null

    override fun start(onChange: (percent: Int, charger: String?) -> Unit) {
        val r = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                val level = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1); val scale = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
                onChange(if (level < 0 || scale <= 0) -1 else level * 100 / scale,
                    when (i.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0)) {
                        android.os.BatteryManager.BATTERY_PLUGGED_USB -> "usb"; android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS -> "inductive"
                        android.os.BatteryManager.BATTERY_PLUGGED_AC -> "usb"; else -> null
                    })
            }
        }
        receiver = r
        context.registerReceiver(r, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
    }

    override fun stop() {
        receiver?.let { context.unregisterReceiver(it) }; receiver = null
    }
}

/**
 * Android's connectivity, as LunaCore's [org.webosarchive.lunacy.core.NetworkSource]: what
 * ConnectionManager read itself before LunaCore. While Android keeps Lunacy off the network
 * ([blocked]: Doze, see SleepWake) there is none.
 */
class AndroidNetwork(private val context: android.content.Context, private val blocked: () -> Boolean = { false }) : org.webosarchive.lunacy.core.NetworkSource {
    private val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    private val wm = context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
    private var receiver: android.content.BroadcastReceiver? = null

    @Suppress("DEPRECATION")
    override fun current(): org.webosarchive.lunacy.core.NetworkSource.Network? {
        val active = cm.activeNetworkInfo?.takeIf { !blocked() } ?: return null
        if (!active.isConnected) return null
        val wifi = if (active.type == android.net.ConnectivityManager.TYPE_WIFI) {
            val info = wm.connectionInfo
            val ip = info.ipAddress
            org.webosarchive.lunacy.core.NetworkSource.Wifi(
                "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}",
                info.ssid.orEmpty().removeSurrounding("\""), info.bssid.orEmpty().uppercase(),
                android.net.wifi.WifiManager.calculateSignalLevel(info.rssi, 3))
        } else null
        return org.webosarchive.lunacy.core.NetworkSource.Network(wifi, active.type == android.net.ConnectivityManager.TYPE_MOBILE)
    }

    override fun watch(onChange: () -> Unit) {
        if (receiver != null) return
        receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: android.content.Intent) = onChange()
        }
        context.registerReceiver(receiver, android.content.IntentFilter().apply {
            addAction(android.net.ConnectivityManager.CONNECTIVITY_ACTION); addAction(android.net.wifi.WifiManager.RSSI_CHANGED_ACTION)
        })
    }

    override fun unwatch() {
        receiver?.let { context.unregisterReceiver(it) }
        receiver = null
    }
}
