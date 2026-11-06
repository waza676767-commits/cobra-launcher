package dev.mango.os.home

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.graphics.RectF
import android.provider.Settings
import dev.mango.os.core.Prefs
import dev.mango.os.core.dp
import dev.mango.os.data.Placement
import dev.mango.os.data.WidgetItem
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Where everything goes on the home screen, worked out from the real window size and system
 * insets. On the Galaxy A56 (1080×2340, 20:9) the defaults give a 4×6 grid like a tall iPhone.
 */
class Geometry {
    var width = 0; private set
    var height = 0; private set
    var insetTop = 0; private set
    var insetBottom = 0; private set
    var insetLeft = 0; private set
    var insetRight = 0; private set

    var cols = 4; private set
    var rows = 6; private set
    var showLabels = true; private set
    var dockOn = true; private set
    var dockSlots = 4; private set
    private var iconScale = 1f

    var hPad = 0f; private set
    var cellW = 0f; private set
    var cellH = 0f; private set
    var wsTop = 0; private set
    var wsHeight = 0; private set
    var iconSize = 0; private set
    var iconTop = 0f; private set
    var labelSize = 0f; private set
    var labelGap = 0f; private set
    var dockTop = 0; private set
    var dockH = 0; private set
    var dockMargin = 0; private set
    var pillTop = 0; private set
    var pillH = 0; private set
    var widgetRadius = 0f; private set

    private var stamp = ""

    fun loadPrefs() {
        cols = Prefs.columns.coerceIn(3, 6)
        rows = Prefs.rows.coerceIn(4, 8)
        showLabels = Prefs.labels
        dockOn = Prefs.dock
        dockSlots = Prefs.dockCount.coerceIn(3, 6)
        iconScale = Prefs.iconScale.coerceIn(0.7f, 1.3f)
        stamp = ""
    }

    /** Returns true if anything moved. */
    fun compute(w: Int, h: Int, top: Int, bottom: Int, left: Int, right: Int): Boolean {
        val key = "$w,$h,$top,$bottom,$left,$right,${1.dp}"
        if (key == stamp) return false
        stamp = key
        width = w; height = h
        insetTop = top; insetBottom = bottom; insetLeft = left; insetRight = right

        val usableW = w - left - right
        hPad = if (cols <= 4) 18.dp else 12.dp
        cellW = (usableW - 2 * hPad) / cols
        labelSize = min(12.dp, cellW * 0.15f)
        labelGap = 5.dp
        var icon = cellW * (if (cols >= 5) 0.76f else 0.7f) * iconScale
        icon = min(icon, cellW * 0.94f)
        val labelBlock = if (showLabels) labelGap + labelSize * 1.35f else 0f
        val bottomGap = max(bottom.toFloat(), 12.dp) + 6.dp

        repeat(2) {
            dockH = if (dockOn) (icon + 2 * 13.dp).roundToInt() else 0
            dockMargin = 10.dp.roundToInt()
            dockTop = (h - bottomGap - dockH).roundToInt()
            pillH = 30.dp.roundToInt()
            val above = if (dockOn) dockTop.toFloat() else h - bottomGap
            pillTop = (above - 12.dp - pillH).roundToInt()
            wsTop = (top + 6.dp).roundToInt()
            wsHeight = (pillTop - 6.dp - wsTop).roundToInt()
            cellH = wsHeight / rows.toFloat()
            if (icon + labelBlock > cellH * 0.94f) icon = cellH * 0.94f - labelBlock
        }
        iconSize = icon.roundToInt().coerceAtLeast(24)
        iconTop = ((cellH - iconSize - labelBlock) / 2f).coerceAtLeast(0f)
        widgetRadius = iconSize * 0.36f
        return true
    }

    fun invalidate() { stamp = "" }

    /** Bounds of a placed item inside its page. */
    fun rectFor(p: Placement, out: RectF) {
        val l = hPad + p.x * cellW
        val t = p.y * cellH
        if (p.item is WidgetItem) {
            val ix = (cellW - iconSize) / 2f
            out.set(l + ix, t + iconTop, l + p.sx * cellW - ix, t + (p.sy - 1) * cellH + iconTop + iconSize)
        } else {
            out.set(l, t, l + cellW, t + cellH)
        }
    }

    /** Size a widget of the given span has on the grid (used for previews too). */
    fun widgetSize(sx: Int, sy: Int): Pair<Int, Int> {
        val ix = (cellW - iconSize)
        return (sx * cellW - ix).roundToInt() to ((sy - 1) * cellH + iconSize).roundToInt()
    }
}

/** Default-home-app helpers (Mango ⇄ One UI Home). */
object HomeSwitch {
    const val REQ_ROLE = 41

    fun isDefault(ctx: Context): Boolean = try {
        ctx.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true
    } catch (e: Exception) {
        false
    }

    /** Shows the system "Set Mango as default home app?" sheet (or the picker if that's unavailable). */
    fun makeDefault(a: Activity) {
        val rm = a.getSystemService(RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
            try {
                @Suppress("DEPRECATION")
                a.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_ROLE)
                return
            } catch (e: Exception) {
            }
        }
        openPicker(a)
    }

    /** System screen to choose the home app: the quickest way back to One UI Home. */
    fun openPicker(ctx: Context) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
