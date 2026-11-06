package dev.aura.launcher.home

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Telephony
import dev.aura.launcher.data.AppItem
import dev.aura.launcher.data.AppRepository
import dev.aura.launcher.data.HomeLayout
import dev.aura.launcher.data.Item
import dev.aura.launcher.data.WidgetItem

/** First-run home screen: widgets on top, everyday apps first, the rest A–Z on the next pages. */
object DefaultLayout {
    private val preferred = listOf(
        "com.sec.android.gallery3d", "com.google.android.apps.photos",
        "com.samsung.android.calendar", "com.google.android.calendar",
        "com.sec.android.app.clockpackage", "com.google.android.deskclock",
        "com.samsung.android.app.notes", "com.google.android.keep",
        "com.google.android.apps.maps", "com.google.android.youtube",
        "com.google.android.gm", "com.whatsapp", "com.instagram.android",
        "com.spotify.music", "com.sec.android.app.myfiles",
        "com.sec.android.app.popupcalculator", "com.google.android.calculator",
        "com.android.vending", "com.android.settings"
    )

    fun build(ctx: Context, cols: Int, rows: Int): HomeLayout {
        val l = HomeLayout()
        val pm = ctx.packageManager
        fun pkgFor(i: Intent): String? = try {
            pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName?.takeIf { it != "android" }
        } catch (e: Exception) { null }

        val dockPkgs = listOfNotNull(
            pkgFor(Intent(Intent.ACTION_DIAL)),
            Telephony.Sms.getDefaultSmsPackage(ctx) ?: pkgFor(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
            pkgFor(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))),
            pkgFor(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        ).distinct()
        val used = HashSet<String>()
        for (p in dockPkgs) AppRepository.forPackage(p)?.let { if (used.add(it.key)) l.dock.add(AppItem(l.newId(), it.key)) }

        val apps = AppRepository.visibleApps().filter { it.key !in used }
        val first = preferred.mapNotNull { p -> apps.firstOrNull { it.pkg == p } }.distinct()
        val rest = apps.filter { it !in first }.sortedBy { it.label.lowercase() }
        val page = ArrayList<Item>()
        page.add(WidgetItem(l.newId(), "weather", 2, 2))
        page.add(WidgetItem(l.newId(), "calendar", 2, 2))
        (first + rest).forEach { page.add(AppItem(l.newId(), it.key)) }
        l.pages.add(page)
        l.reflow(cols, rows, dropEmpty = true)
        return l
    }

    /** Folder name from the apps' Play Store category, like iOS suggests. */
    fun folderName(vararg keys: String): String {
        for (k in keys) {
            val cat = AppRepository[k]?.category ?: continue
            val n = when (cat) {
                ApplicationInfo.CATEGORY_GAME -> "Games"
                ApplicationInfo.CATEGORY_AUDIO -> "Music"
                ApplicationInfo.CATEGORY_VIDEO -> "Entertainment"
                ApplicationInfo.CATEGORY_IMAGE -> "Photo & Video"
                ApplicationInfo.CATEGORY_SOCIAL -> "Social"
                ApplicationInfo.CATEGORY_NEWS -> "News"
                ApplicationInfo.CATEGORY_MAPS -> "Travel"
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
                ApplicationInfo.CATEGORY_ACCESSIBILITY -> "Utilities"
                else -> null
            }
            if (n != null) return n
        }
        return "Folder"
    }
}
