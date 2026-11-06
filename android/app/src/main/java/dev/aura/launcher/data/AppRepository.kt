package dev.aura.launcher.data

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import dev.aura.launcher.core.Prefs

class AppEntry(val key: String, val info: LauncherActivityInfo, val user: UserHandle, val label: String) {
    val component: ComponentName get() = info.componentName
    val pkg: String get() = info.componentName.packageName
    val isSystem: Boolean get() = (info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    val isUpdatedSystem: Boolean get() = (info.applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    val category: Int get() = info.applicationInfo.category
    val firstInstall: Long get() = try { info.firstInstallTime } catch (e: Exception) { 0L }
}

/** Every launchable app on the phone, kept up to date through LauncherApps callbacks. */
object AppRepository {
    val apps = LinkedHashMap<String, AppEntry>()
    private val listeners = ArrayList<() -> Unit>()
    private lateinit var app: Context
    private lateinit var la: LauncherApps
    private lateinit var um: UserManager
    private val main = Handler(Looper.getMainLooper())
    private var homeAlias = ""

    fun init(ctx: Context) {
        app = ctx.applicationContext
        la = app.getSystemService(LauncherApps::class.java)
        um = app.getSystemService(UserManager::class.java)
        homeAlias = app.packageName + ".AuraHome"
        reload()
        la.registerCallback(callback, main)
    }

    fun listen(l: () -> Unit) { listeners.add(l) }
    fun unlisten(l: () -> Unit) { listeners.remove(l) }

    fun reload() {
        apps.clear()
        val found = ArrayList<AppEntry>()
        for (user in la.profiles) {
            val list = try { la.getActivityList(null, user) } catch (e: Exception) { emptyList() }
            for (info in list) {
                if (info.componentName.className == homeAlias) continue
                val key = keyOf(info.componentName, user)
                found.add(AppEntry(key, info, user, info.label?.toString()?.trim().orEmpty().ifEmpty { info.componentName.packageName }))
            }
        }
        found.sortBy { it.label.lowercase() }
        found.forEach { apps[it.key] = it }
    }

    fun keyOf(cn: ComponentName, user: UserHandle): String =
        if (user == Process.myUserHandle()) cn.flattenToShortString()
        else cn.flattenToShortString() + "#" + um.getSerialNumberForUser(user)

    operator fun get(key: String): AppEntry? = apps[key]

    fun forPackage(pkg: String): AppEntry? = apps.values.firstOrNull { it.pkg == pkg && it.user == Process.myUserHandle() }
        ?: apps.values.firstOrNull { it.pkg == pkg }

    fun visibleApps(): List<AppEntry> {
        val hidden = Prefs.hidden
        return apps.values.filter { it.key !in hidden }
    }

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed()
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed()
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed()
    }

    private fun changed() {
        reload()
        listeners.toList().forEach { it() }
    }

    fun launch(ctx: Context, key: String, from: View?): Boolean {
        val e = apps[key] ?: return false
        Prefs.countLaunch(key)
        val opts = from?.let { v ->
            val w = v.width; val h = v.height
            ActivityOptions.makeScaleUpAnimation(v, 0, 0, w, h)
        }
        val bounds = from?.let { v ->
            val loc = IntArray(2); v.getLocationOnScreen(loc)
            Rect(loc[0], loc[1], loc[0] + v.width, loc[1] + v.height)
        }
        return try {
            la.startMainActivity(e.component, e.user, bounds, opts?.toBundle())
            true
        } catch (ex: Exception) {
            Toast.makeText(ctx, "Can't open ${e.label}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun openAppInfo(ctx: Context, key: String) {
        val e = apps[key] ?: return
        try {
            la.startAppDetailsActivity(e.component, e.user, null, null)
        } catch (ex: Exception) {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${e.pkg}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun uninstall(ctx: Context, key: String) {
        val e = apps[key] ?: return
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:${e.pkg}"))
                    .putExtra(Intent.EXTRA_USER, e.user)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (ex: Exception) {
            openAppInfo(ctx, key)
        }
    }

    // App shortcuts (the "Compose", "New tab"… entries). Only allowed while Aura is the home app.
    fun shortcuts(key: String): List<ShortcutInfo> {
        val e = apps[key] ?: return emptyList()
        if (!la.hasShortcutHostPermission()) return emptyList()
        return try {
            val q = LauncherApps.ShortcutQuery()
                .setPackage(e.pkg)
                .setActivity(e.component)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            (la.getShortcuts(q, e.user) ?: emptyList())
                .filter { it.isEnabled && (it.isDynamic || it.isDeclaredInManifest) }
                .sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank }))
                .take(4)
        } catch (ex: Exception) {
            emptyList()
        }
    }

    fun shortcutIcon(s: ShortcutInfo, density: Int) = try { la.getShortcutIconDrawable(s, density) } catch (e: Exception) { null }

    fun startShortcut(s: ShortcutInfo) {
        try { la.startShortcut(s, null, null) } catch (e: Exception) { }
    }
}
