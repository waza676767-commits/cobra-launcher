package dev.mango.os.data

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * Custom names and icons made in the Mathy app. Mango OS reads them from Mathy's provider and
 * listens for changes, so a rename or new icon shows up instantly everywhere: home screen, dock,
 * folders, App Library, search and lock screen notifications.
 */
object Overrides {
    const val MATHY = "dev.mango.mathy"
    private const val AUTHORITY = "dev.mango.mathy.overrides"
    private val APPS: Uri = Uri.parse("content://$AUTHORITY/apps")

    class Look(val label: String?, val icon: Bitmap?, val version: Long)

    @Volatile
    var looks: Map<String, Look> = emptyMap()
        private set
    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var observing = false
    private var onChange: (() -> Unit)? = null

    fun label(key: String): String? = looks[key]?.label
    fun icon(key: String): Bitmap? = looks[key]?.icon
    fun has(key: String) = looks.containsKey(key)

    fun init(ctx: Context, changed: () -> Unit) {
        app = ctx.applicationContext
        onChange = changed
        refresh()
    }

    fun mathyInstalled(ctx: Context) = try {
        ctx.packageManager.getPackageInfo(MATHY, 0); true
    } catch (e: Exception) {
        false
    }

    /** Opens Mathy's editor for one app. Returns false if Mathy isn't installed. */
    fun edit(ctx: Context, key: String): Boolean = try {
        ctx.startActivity(Intent("dev.mango.mathy.EDIT").setPackage(MATHY).putExtra("key", key).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    /** (Re)loads everything; also called when apps are installed / removed (Mathy may have come or gone). */
    fun refresh() {
        val ctx = app ?: return
        if (!observing) {
            observing = try {
                ctx.contentResolver.registerContentObserver(APPS, true, observer)
                true
            } catch (e: Exception) {
                false // Mathy isn't installed yet
            }
        }
        val before = looks
        exec.execute {
            val out = HashMap<String, Look>()
            try {
                ctx.contentResolver.query(APPS, null, null, null, null)?.use { c ->
                    val ki = c.getColumnIndex("key")
                    val li = c.getColumnIndex("label")
                    val ii = c.getColumnIndex("icon")
                    while (c.moveToNext()) {
                        val key = c.getString(ki) ?: continue
                        val label = c.getString(li)?.takeIf { it.isNotBlank() }
                        val version = c.getLong(ii)
                        var icon: Bitmap? = null
                        if (version > 0) {
                            val old = before[key]
                            icon = if (old != null && old.version == version) old.icon else try {
                                ctx.contentResolver.openInputStream(Uri.parse("content://$AUTHORITY/icon/" + Uri.encode(key)))?.use { BitmapFactory.decodeStream(it) }
                            } catch (e: Exception) {
                                null
                            }
                        }
                        out[key] = Look(label, icon, version)
                    }
                }
            } catch (e: Exception) {
                // Mathy missing or signed with a different key: no custom looks
            }
            main.post {
                val changed = out.keys != looks.keys || out.any { (k, v) -> looks[k]?.let { it.label != v.label || it.version != v.version } ?: true }
                looks = out
                if (changed) onChange?.invoke()
            }
        }
    }
}
