package dev.mango.mathy

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** One app's custom look: a new name and/or a new icon (PNG on disk). */
class Override(val key: String, val label: String?, val iconVersion: Long, val meta: JSONObject?)

/** An installed app you can restyle. [key] matches the key Mango OS uses for the same app. */
class AppInfo(val key: String, val label: String, val info: LauncherActivityInfo, val user: UserHandle) {
    val pkg: String get() = info.componentName.packageName
}

/**
 * Where Mathy keeps your changes. Mango OS reads them through [OverridesProvider] and is told
 * about every change, so the home screen updates straight away.
 */
object Store {
    const val AUTHORITY = "dev.mango.mathy.overrides"
    val APPS_URI: Uri = Uri.parse("content://$AUTHORITY/apps")

    private fun sp(ctx: Context) = ctx.getSharedPreferences("mathy", Context.MODE_PRIVATE)

    private fun json(ctx: Context): JSONObject = try {
        JSONObject(sp(ctx).getString("overrides", "{}") ?: "{}")
    } catch (e: Exception) {
        JSONObject()
    }

    fun all(ctx: Context): List<Override> {
        val o = json(ctx)
        val out = ArrayList<Override>()
        for (k in o.keys()) {
            val v = o.optJSONObject(k) ?: continue
            out.add(Override(k, v.optString("label").takeIf { it.isNotBlank() }, v.optLong("icon", 0L), v.optJSONObject("meta")))
        }
        return out
    }

    fun get(ctx: Context, key: String): Override? = all(ctx).firstOrNull { it.key == key }

    fun iconFile(ctx: Context, key: String): File {
        val dir = File(ctx.filesDir, "icons")
        dir.mkdirs()
        val h = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$h.png")
    }

    fun loadIcon(ctx: Context, key: String): Bitmap? {
        val f = iconFile(ctx, key)
        return if (f.exists()) BitmapFactory.decodeFile(f.path) else null
    }

    /**
     * [label]: new name (null = keep the real one).
     * [icon]: new icon, or null to go back to the real icon.
     */
    fun save(ctx: Context, key: String, label: String?, icon: Bitmap?, meta: JSONObject? = null) {
        val o = json(ctx)
        var version = 0L
        val f = iconFile(ctx, key)
        if (icon != null) {
            f.outputStream().use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
            version = System.currentTimeMillis()
        } else f.delete()
        if (label.isNullOrBlank() && version == 0L) o.remove(key)
        else o.put(key, JSONObject().put("label", label ?: "").put("icon", version).put("meta", meta ?: JSONObject()))
        sp(ctx).edit().putString("overrides", o.toString()).commit()
        ctx.contentResolver.notifyChange(APPS_URI, null)
    }

    fun reset(ctx: Context, key: String) = save(ctx, key, null, null)

    fun resetAll(ctx: Context) {
        for (o in all(ctx)) iconFile(ctx, o.key).delete()
        sp(ctx).edit().putString("overrides", "{}").commit()
        ctx.contentResolver.notifyChange(APPS_URI, null)
    }

    // ------------------------------------------------------------------ installed apps

    fun keyOf(ctx: Context, cn: ComponentName, user: UserHandle): String =
        if (user == Process.myUserHandle()) cn.flattenToShortString()
        else cn.flattenToShortString() + "#" + ctx.getSystemService(UserManager::class.java).getSerialNumberForUser(user)

    fun apps(ctx: Context): List<AppInfo> {
        val la = ctx.getSystemService(LauncherApps::class.java)
        val out = ArrayList<AppInfo>()
        for (user in la.profiles) {
            val list = try { la.getActivityList(null, user) } catch (e: Exception) { emptyList() }
            for (info in list) {
                if (info.componentName.className == "dev.mango.os.MangoHome") continue
                out.add(AppInfo(keyOf(ctx, info.componentName, user), info.label?.toString()?.trim().orEmpty().ifEmpty { info.componentName.packageName }, info, user))
            }
        }
        return out.sortedBy { it.label.lowercase() }
    }

    fun mangoInstalled(ctx: Context) = try {
        ctx.packageManager.getPackageInfo("dev.mango.os", 0); true
    } catch (e: Exception) {
        false
    }
}
