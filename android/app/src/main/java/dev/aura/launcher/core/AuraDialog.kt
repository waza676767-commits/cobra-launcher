package dev.aura.launcher.core

import android.app.Activity
import android.app.Dialog
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import dev.aura.launcher.R

/** iOS-style alert: rounded frosted card, centred title, stacked or side-by-side buttons. */
object AuraDialog {
    class Action(val label: String, val style: Style = Style.NORMAL, val run: () -> Unit = {})
    enum class Style { NORMAL, BOLD, DESTRUCTIVE }

    fun show(a: Activity, title: String, message: String?, actions: List<Action>): Dialog {
        val d = Dialog(a, R.style.Theme_Aura_Dialog)
        val panel = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 26.dp
                setColor(if (Theme.dark) 0xF02A2A2C.toInt() else 0xF7F6F6F8.toInt())
            }
            clipToOutline = true
        }
        val pad = 20.dpi
        panel.addView(TextView(a).apply {
            text = title
            setTextColor(Theme.text)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 17.dp)
            typeface = Ui.font(600)
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, if (message == null) pad else 4.dpi)
        })
        if (message != null) panel.addView(TextView(a).apply {
            text = message
            setTextColor(Theme.text)
            alpha = 0.85f
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 13.5f.dp)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
            setPadding(pad, 0, pad, pad)
        })
        val side = actions.size == 2
        val row = LinearLayout(a).apply { orientation = if (side) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        for ((i, act) in actions.withIndex()) {
            if (side && i > 0) row.addView(View(a).apply { setBackgroundColor(Theme.separator) }, LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT))
            if (!side) row.addView(View(a).apply { setBackgroundColor(Theme.separator) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
            val b = TextView(a).apply {
                text = act.label
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_PX, 17.dp)
                typeface = Ui.font(if (act.style == Style.BOLD) 600 else 400)
                setTextColor(if (act.style == Style.DESTRUCTIVE) Theme.red else Theme.accent)
                val tv = TypedValue()
                a.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                setBackgroundResource(tv.resourceId)
                setOnClickListener { d.dismiss(); act.run() }
            }
            row.addView(b, if (side) LinearLayout.LayoutParams(0, 46.dpi, 1f) else LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 46.dpi))
        }
        if (side) panel.addView(View(a).apply { setBackgroundColor(Theme.separator) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
        panel.addView(row)
        d.setContentView(panel, LinearLayout.LayoutParams(280.dpi, LinearLayout.LayoutParams.WRAP_CONTENT))
        d.window?.let { w ->
            w.setLayout(280.dpi, WindowManager.LayoutParams.WRAP_CONTENT)
            if (w.windowManager.isCrossWindowBlurEnabled && !Theme.reduceTransparency) {
                w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                w.attributes = w.attributes.apply { blurBehindRadius = 24.dpi }
            }
        }
        d.show()
        return d
    }
}
