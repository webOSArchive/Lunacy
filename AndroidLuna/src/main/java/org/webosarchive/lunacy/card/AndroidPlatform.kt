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

    override fun beginTransaction() = db.beginTransaction()
    override fun setTransactionSuccessful() = db.setTransactionSuccessful()
    override fun endTransaction() = db.endTransaction()
    override fun close() = db.close()
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
