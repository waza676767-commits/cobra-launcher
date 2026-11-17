package dev.mango.mathy

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextPaint
import android.text.TextUtils
import android.text.TextWatcher
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Mathy: pick any app, give it a new name and a new icon. Mango OS picks the change up instantly
 * (home screen, dock, folders, App Library, search, lock screen notifications).
 */
class MathyActivity : Activity() {
    private lateinit var pal: Pal
    private lateinit var root: FrameLayout
    private lateinit var list: ListView
    private lateinit var adapter: AppsAdapter
    private var editor: Editor? = null
    private var apps: List<AppInfo> = emptyList()
    private var query = ""
    private var insets = android.graphics.Insets.NONE
    private var openedForEdit = false
    private val exec = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val icons = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val loading = HashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        U.init(this)
        pal = Pal(U.dark(this))
        window.setDecorFitsSystemWindows(false)
        root = FrameLayout(this).apply { setBackgroundColor(pal.bg) }
        list = ListView(this).apply {
            divider = null
            selector = android.graphics.drawable.ColorDrawable(0)
            isVerticalScrollBarEnabled = false
            clipToPadding = false
        }
        list.addHeaderView(Header(), null, false)
        adapter = AppsAdapter()
        list.adapter = adapter
        root.addView(list, FrameLayout.LayoutParams(-1, -1))
        root.setOnApplyWindowInsetsListener { _, ins ->
            insets = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val ime = ins.getInsets(WindowInsets.Type.ime()).bottom
            list.setPadding(0, insets.top, 0, maxOf(insets.bottom, ime) + 24.dpi)
            editor?.applyInsets(ime)
            ins
        }
        setContentView(root)
        window.insetsController?.setSystemBarsAppearance(
            if (U.dark(this)) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        )
        reloadApps()
        handle(intent)
        debug = DebugHooks.attach(this)
    }

    private var debug: List<android.content.BroadcastReceiver> = emptyList()

    override fun onDestroy() {
        debug.forEach { try { unregisterReceiver(it) } catch (e: Exception) {} }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Mango OS can open the editor for one app directly ("Change Icon & Name…"). */
    private fun handle(i: Intent?) {
        val key = i?.getStringExtra("key") ?: return
        openedForEdit = true
        val open: () -> Unit = {
            apps.firstOrNull { it.key == key }?.let { openEditor(it, animate = false) }
        }
        if (apps.isEmpty()) pendingOpen = open else open()
    }

    private var pendingOpen: (() -> Unit)? = null

    private fun reloadApps() {
        exec.execute {
            val a = Store.apps(this)
            main.post {
                apps = a
                adapter.rebuild()
                pendingOpen?.invoke()
                pendingOpen = null
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val e = editor
        if (e != null) closeEditor() else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    // ------------------------------------------------------------------ icons for the list

    private fun iconFor(app: AppInfo, cb: () -> Unit): Bitmap? {
        icons.get(app.key)?.let { return it }
        if (loading.add(app.key)) {
            exec.execute {
                val b = IconArt.current(this, app, 44.dpi * 2)
                main.post { loading.remove(app.key); icons.put(app.key, b); cb() }
            }
        }
        return null
    }

    // ------------------------------------------------------------------ list

    private inner class Header : LinearLayout(this@MathyActivity) {
        init {
            orientation = VERTICAL
            setPadding(20.dpi, 18.dpi, 20.dpi, 6.dpi)
            val top = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            top.addView(object : View(context) {
                val icon: Bitmap by lazy {
                    val full = Bitmap.createBitmap(IconArt.SIZE, IconArt.SIZE, Bitmap.Config.ARGB_8888)
                    val d = context.getDrawable(R.mipmap.ic_mathy) as? android.graphics.drawable.AdaptiveIconDrawable
                    val o = IconArt.SIZE / 4
                    d?.background?.let { it.setBounds(-o, -o, IconArt.SIZE + o, IconArt.SIZE + o); it.draw(Canvas(full)) }
                    d?.foreground?.let { it.setBounds(-o, -o, IconArt.SIZE + o, IconArt.SIZE + o); it.draw(Canvas(full)) }
                    IconArt.masked(full, 52.dpi * 2)
                }
                override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(52.dpi, 52.dpi)
                override fun onDraw(c: Canvas) = c.drawBitmap(icon, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            })
            top.addView(text("Mathy", 34.dp, pal.text, 700).apply { setPadding(14.dpi, 0, 0, 0) })
            addView(top)
            addView(text("Give any app a new name and a new icon.", 15.dp, pal.text2).apply { setPadding(0, 10.dpi, 0, 0) })
            val mango = Store.mangoInstalled(context)
            addView(text(
                if (mango) "✓  Mango OS shows your changes on the Home Screen, in folders, the App Library and Search."
                else "Install Mango OS to see your changes: other home screens can't change app icons.",
                13.5f.dp, if (mango) pal.green else pal.red, 500
            ).apply { setPadding(0, 8.dpi, 0, 14.dpi); setLineSpacing(0f, 1.15f) })
            // search
            val search = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply { cornerRadius = 22.dp; setColor(pal.fill) }
                setPadding(14.dpi, 0, 14.dpi, 0)
            }
            search.addView(object : View(context) {
                override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(22.dpi, 22.dpi)
                override fun onDraw(c: Canvas) = G.search(c, width / 2f, height / 2f, 18.dp, pal.text2)
            })
            search.addView(EditText(context).apply {
                hint = "Search apps"
                setHintTextColor(pal.text2)
                setTextColor(pal.text)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, 17.dp)
                background = null
                isSingleLine = true
                imeOptions = EditorInfo.IME_ACTION_SEARCH
                setPadding(10.dpi, 0, 0, 0)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(e: Editable?) { query = e?.toString().orEmpty().trim(); adapter.rebuild() }
                })
            }, LayoutParams(0, 44.dpi, 1f))
            addView(search)
        }
    }

    private fun text(s: CharSequence, size: Float, color: Int, weight: Int = 400) = TextView(this).apply {
        text = s
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        typeface = U.font(weight)
    }

    /** Rows: section titles and apps (first/last app of a section get rounded corners). */
    private inner class AppsAdapter : BaseAdapter() {
        inner class Row(val title: String?, val app: AppInfo?, val first: Boolean, val last: Boolean)
        private var rows: List<Row> = emptyList()
        private var overrides: Map<String, Override> = emptyMap()

        fun rebuild() {
            overrides = Store.all(this@MathyActivity).associateBy { it.key }
            val q = query.lowercase()
            val match = apps.filter { a ->
                q.isEmpty() || a.label.lowercase().contains(q) || (overrides[a.key]?.label?.lowercase()?.contains(q) == true)
            }
            val changed = match.filter { overrides.containsKey(it.key) }
            val out = ArrayList<Row>()
            fun section(title: String, list: List<AppInfo>) {
                if (list.isEmpty()) return
                out.add(Row(title, null, false, false))
                list.forEachIndexed { i, a -> out.add(Row(null, a, i == 0, i == list.size - 1)) }
            }
            section("Changed", changed)
            section(if (q.isEmpty()) "All Apps" else "Results", match.filter { it !in changed })
            rows = out
            notifyDataSetChanged()
        }

        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getViewTypeCount() = 2
        override fun getItemViewType(position: Int) = if (rows[position].title != null) 0 else 1
        override fun isEnabled(position: Int) = rows[position].app != null

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val r = rows[position]
            if (r.title != null) {
                val v = (convertView as? TextView) ?: text("", 13.dp, pal.text2, 500).apply { setPadding(36.dpi, 22.dpi, 36.dpi, 8.dpi) }
                v.text = r.title.uppercase()
                return v
            }
            val v = (convertView as? AppRow) ?: AppRow()
            v.bind(r.app!!, overrides[r.app.key], r.first, r.last)
            return v
        }
    }

    private inner class AppRow : View(this@MathyActivity) {
        private var app: AppInfo? = null
        private var o: Override? = null
        private var first = false
        private var last = false
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        private val sep = Paint()
        private val bp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()
        private val path = android.graphics.Path()
        private var pressed = false

        init {
            setOnClickListener { app?.let { openEditor(it, animate = true) } }
        }

        fun bind(a: AppInfo, ov: Override?, f: Boolean, l: Boolean) {
            app = a; o = ov; first = f; last = l
            invalidate()
        }

        override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), 62.dpi)

        override fun onDraw(c: Canvas) {
            val a = app ?: return
            val m = 16.dp
            val rad = 22.dp
            r.set(m, 0f, width - m, height.toFloat())
            path.reset()
            path.addRoundRect(r, floatArrayOf(
                if (first) rad else 0f, if (first) rad else 0f, if (first) rad else 0f, if (first) rad else 0f,
                if (last) rad else 0f, if (last) rad else 0f, if (last) rad else 0f, if (last) rad else 0f
            ), android.graphics.Path.Direction.CW)
            bg.color = if (pressed) pal.fill else pal.group
            c.drawPath(path, bg)
            val s = 42.dp
            val il = m + 14.dp
            val itop = (height - s) / 2f
            iconFor(a) { invalidate() }?.let { b -> r.set(il, itop, il + s, itop + s); c.drawBitmap(b, null, r, bp) }
            val tx = il + s + 14.dp
            val newName = o?.label
            tp.color = pal.text
            tp.textSize = 17.dp
            tp.typeface = U.font(if (newName != null) 600 else 400)
            val maxW = width - tx - m - 40.dp
            val title = TextUtils.ellipsize(newName ?: a.label, tp, maxW, TextUtils.TruncateAt.END)
            val sub = when {
                newName != null && (o?.iconVersion ?: 0L) > 0 -> "Was “${a.label}” · new icon"
                newName != null -> "Was “${a.label}”"
                (o?.iconVersion ?: 0L) > 0 -> "New icon"
                else -> null
            }
            if (sub != null) {
                c.drawText(title, 0, title.length, tx, height / 2f - 3.dp, tp)
                tp.color = pal.text2
                tp.textSize = 13.dp
                tp.typeface = U.font(400)
                val st = TextUtils.ellipsize(sub, tp, maxW, TextUtils.TruncateAt.END)
                c.drawText(st, 0, st.length, tx, height / 2f + 15.dp, tp)
            } else c.drawText(title, 0, title.length, tx, height / 2f + 6.dp, tp)
            G.chevron(c, width - m - 20.dp, height / 2f, 14.dp, pal.text3)
            if (!last) {
                sep.color = pal.sep
                c.drawRect(tx, height - 0.6f.dp, width - m, height.toFloat(), sep)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { pressed = true; invalidate() }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { pressed = false; invalidate() }
            }
            return super.onTouchEvent(event)
        }
    }

    // ------------------------------------------------------------------ editor

    private fun openEditor(app: AppInfo, animate: Boolean) {
        if (editor != null) return
        val e = Editor(app)
        editor = e
        root.addView(e, FrameLayout.LayoutParams(-1, -1))
        e.applyInsets(0)
        if (animate) {
            e.translationX = root.width.toFloat()
            e.animate().translationX(0f).setDuration(340).setInterpolator(android.view.animation.PathInterpolator(0.16f, 1f, 0.3f, 1f)).withLayer().start()
            list.animate().translationX(-root.width * 0.3f).alpha(0.5f).setDuration(340).withLayer().start()
        } else {
            list.visibility = View.INVISIBLE
        }
    }

    private fun closeEditor() {
        val e = editor ?: return
        editor = null
        hideKeyboard(e)
        if (openedForEdit) { finish(); return }
        list.visibility = View.VISIBLE
        list.animate().translationX(0f).alpha(1f).setDuration(320).withLayer().start()
        e.animate().translationX(root.width.toFloat()).setDuration(320).withLayer().withEndAction { root.removeView(e) }.start()
        adapter.rebuild()
    }

    private fun hideKeyboard(v: View) {
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(v.windowToken, 0)
    }

    private var photoTarget: ((Bitmap?) -> Unit)? = null

    private fun pickPhoto(done: (Bitmap?) -> Unit) {
        photoTarget = done
        val i = if (Build.VERSION.SDK_INT >= 33) Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*")
        else Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ_PHOTO)
        } catch (e: Exception) {
            Toast.makeText(this, "No photo picker found", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PHOTO) return
        val uri: Uri? = if (resultCode == RESULT_OK) data?.data else null
        val target = photoTarget ?: return
        photoTarget = null
        if (uri == null) return
        exec.execute {
            val b = IconArt.photo(this, uri)
            main.post { target(b) }
        }
    }

    private fun pickApp(exclude: String, done: (AppInfo) -> Unit) {
        val choices = apps.filter { it.key != exclude }
        val ad = object : BaseAdapter() {
            override fun getCount() = choices.size
            override fun getItem(position: Int) = choices[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val a = choices[position]
                val row = (convertView as? LinearLayout) ?: LinearLayout(this@MathyActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(20.dpi, 8.dpi, 20.dpi, 8.dpi)
                    addView(object : View(context) { override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(40.dpi, 40.dpi) })
                    addView(text("", 16.dp, pal.text).apply { setPadding(14.dpi, 0, 0, 0) })
                }
                val iconView = row.getChildAt(0)
                (row.getChildAt(1) as TextView).text = a.label
                iconView.tag = a.key
                iconView.background = null
                val b = iconFor(a) { if (iconView.tag == a.key) notifyDataSetChanged() }
                if (b != null) iconView.background = android.graphics.drawable.BitmapDrawable(resources, b)
                return row
            }
        }
        AlertDialog.Builder(this).setTitle("Use the icon of…").setAdapter(ad) { _, which -> done(choices[which]) }
            .setNegativeButton("Cancel", null).show()
    }

    /** Edit one app: name, quick looks, and icon (Original / Symbol / Photo / App). */
    @SuppressLint("ViewConstructor")
    private inner class Editor(private val app: AppInfo) : FrameLayout(this@MathyActivity) {
        private val existing = Store.get(context, app.key)
        private var mode = existing?.meta?.optString("mode")?.takeIf { it.isNotEmpty() }
            ?: if ((existing?.iconVersion ?: 0L) > 0) "photo" else "original"
        private var symbol = existing?.meta?.optString("symbol")?.takeIf { it.isNotEmpty() } ?: "📘"
        private var grad = existing?.meta?.optInt("grad", 0) ?: 0
        private var photo: Bitmap? = if (mode == "photo" || mode == "app") Store.loadIcon(context, app.key) else null
        private var appIcon: Bitmap? = if (mode == "app") photo else null
        private var appIconFrom = existing?.meta?.optString("from").orEmpty()
        private val preview = PreviewCard(context)
        private val name = EditText(context)
        private val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        private val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false; clipToPadding = false }
        private val bar = FrameLayout(context)
        private val modeArea = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        private lateinit var seg: Segmented
        private val modes = listOf("original", "symbol", "photo", "app")
        private val symbolField = EditText(context)

        init {
            setBackgroundColor(pal.bg)
            isClickable = true
            scroll.addView(body)
            addView(scroll, LayoutParams(-1, -1))
            addView(bar, LayoutParams(-1, 52.dpi))
            // top bar: Cancel · title · Save
            bar.setBackgroundColor(pal.bg)
            bar.addView(text("Cancel", 17.dp, pal.accent).apply {
                setPadding(20.dpi, 12.dpi, 20.dpi, 12.dpi)
                setOnClickListener { closeEditor() }
            }, LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL))
            bar.addView(text("Edit App", 17.dp, pal.text, 600), LayoutParams(-2, -2, Gravity.CENTER))
            bar.addView(text("Save", 17.dp, pal.accent, 600).apply {
                setPadding(20.dpi, 12.dpi, 20.dpi, 12.dpi)
                setOnClickListener { save() }
            }, LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL))

            body.setPadding(16.dpi, 8.dpi, 16.dpi, 40.dpi)
            body.addView(preview)

            // name
            header("Name")
            val nameBox = box()
            name.apply {
                setText(existing?.label ?: app.label)
                hint = app.label
                setHintTextColor(pal.text2)
                setTextColor(pal.text)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, 17.dp)
                background = null
                isSingleLine = true
                imeOptions = EditorInfo.IME_ACTION_DONE
                setPadding(16.dpi, 14.dpi, 16.dpi, 14.dpi)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(e: Editable?) { preview.label = e?.toString()?.ifBlank { app.label } ?: app.label }
                })
            }
            nameBox.addView(name)
            body.addView(nameBox)
            body.addView(footer("Real name: ${app.label}. Search in Mango OS finds the new name."))

            // quick looks
            header("Quick Looks")
            val presets = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for ((sym, g, label) in IconArt.presets) {
                val tile = PresetTile(context, pal, IconArt.masked(IconArt.symbol(sym, g), 56.dpi * 2), label)
                tile.setOnClickListener {
                    symbol = sym; grad = g; mode = "symbol"
                    name.setText(label)
                    symbolField.setText(sym)
                    seg.select(1)
                    showMode()
                }
                presets.addView(tile)
            }
            body.addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(presets) })

            // icon
            header("Icon")
            seg = Segmented(context, pal, listOf("Original", "Symbol", "Photo", "App"), modes.indexOf(mode).coerceAtLeast(0)) { i ->
                mode = modes[i]
                showMode()
            }
            body.addView(seg)
            body.addView(modeArea, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12.dpi })

            // reset
            if (existing != null) {
                val resetBox = box()
                resetBox.addView(text("Reset to Original", 17.dp, pal.red).apply {
                    gravity = Gravity.CENTER
                    setPadding(16.dpi, 14.dpi, 16.dpi, 14.dpi)
                    setOnClickListener {
                        Store.reset(context, app.key)
                        icons.remove(app.key)
                        Toast.makeText(context, "${app.label} is back to normal", Toast.LENGTH_SHORT).show()
                        closeEditor()
                    }
                })
                body.addView(resetBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 28.dpi })
            }

            symbolField.setText(symbol)
            showMode()
            preview.label = existing?.label ?: app.label
        }

        fun applyInsets(ime: Int) {
            (bar.layoutParams as LayoutParams).topMargin = insets.top
            bar.requestLayout()
            scroll.setPadding(0, insets.top + 52.dpi, 0, maxOf(insets.bottom, ime))
        }

        private fun header(s: String) = body.addView(text(s.uppercase(), 13.dp, pal.text2, 500).apply { setPadding(20.dpi, 24.dpi, 20.dpi, 8.dpi) })
        private fun footer(s: String) = text(s, 13.dp, pal.text2).apply { setPadding(20.dpi, 8.dpi, 20.dpi, 0) }
        private fun box() = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = 22.dp; setColor(pal.group) }
        }

        private fun button(label: String, onClick: () -> Unit): View {
            val b = box()
            b.addView(text(label, 17.dp, pal.accent).apply {
                gravity = Gravity.CENTER
                setPadding(16.dpi, 14.dpi, 16.dpi, 14.dpi)
                setOnClickListener { onClick() }
            })
            return b
        }

        /** Square icon for the current choice, or null for the real icon. */
        private fun chosen(): Bitmap? = when (mode) {
            "symbol" -> IconArt.symbol(symbol, grad)
            "photo" -> photo
            "app" -> appIcon
            else -> null
        }

        private fun refreshPreview() {
            val sq = chosen()
            preview.icon = if (sq != null) IconArt.masked(sq, 96.dpi * 2) else IconArt.original(app, 96.dpi * 2)
        }

        private fun showMode() {
            modeArea.removeAllViews()
            when (mode) {
                "symbol" -> {
                    val fieldBox = box()
                    symbolField.apply {
                        hint = "Emoji or up to 3 letters"
                        setHintTextColor(pal.text2)
                        setTextColor(pal.text)
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, 20.dp)
                        background = null
                        isSingleLine = true
                        setPadding(16.dpi, 12.dpi, 16.dpi, 12.dpi)
                        if (tag == null) {
                            tag = "w"
                            addTextChangedListener(object : TextWatcher {
                                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                                override fun afterTextChanged(e: Editable?) {
                                    val t = e?.toString().orEmpty()
                                    symbol = if (t.all { it.isLetterOrDigit() || it == ' ' }) t.take(3) else t
                                    refreshPreview()
                                }
                            })
                        }
                    }
                    (symbolField.parent as? ViewGroup)?.removeView(symbolField)
                    fieldBox.addView(symbolField)
                    modeArea.addView(fieldBox)
                    val grid = box().apply { setPadding(8.dpi, 8.dpi, 8.dpi, 8.dpi) }
                    grid.addView(EmojiGrid(context, IconArt.emojis) { e -> symbol = e; symbolField.setText(e) })
                    modeArea.addView(grid, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12.dpi })
                    modeArea.addView(text("COLOUR", 13.dp, pal.text2, 500).apply { setPadding(20.dpi, 20.dpi, 20.dpi, 8.dpi) })
                    val sw = box().apply { setPadding(8.dpi, 8.dpi, 8.dpi, 8.dpi) }
                    sw.addView(Swatches(context, pal, grad) { g -> grad = g; refreshPreview() })
                    modeArea.addView(sw)
                }
                "photo" -> {
                    modeArea.addView(button(if (photo == null) "Choose a Photo" else "Choose Another Photo") {
                        pickPhoto { b -> if (b != null) { photo = b; refreshPreview(); showMode() } }
                    })
                    modeArea.addView(footer("Any picture works; it's cropped to a square."))
                }
                "app" -> {
                    modeArea.addView(button(if (appIcon == null) "Choose an App's Icon" else "Choose Another App") {
                        pickApp(app.key) { other ->
                            appIcon = IconArt.fromApp(other)
                            appIconFrom = other.key
                            refreshPreview()
                            showMode()
                        }
                    })
                    modeArea.addView(footer("Makes this app look like another one (e.g. Calculator)."))
                }
                else -> modeArea.addView(footer("Keeps the app's real icon. You can still change the name."))
            }
            refreshPreview()
        }

        private fun save() {
            val typed = name.text.toString().trim()
            val label = typed.takeIf { it.isNotEmpty() && it != app.label }
            val icon = chosen()
            if ((mode == "photo" && photo == null) || (mode == "app" && appIcon == null)) {
                Toast.makeText(context, if (mode == "photo") "Choose a photo first" else "Choose an app first", Toast.LENGTH_SHORT).show()
                return
            }
            val meta = JSONObject().put("mode", mode).put("symbol", symbol).put("grad", grad).put("from", appIconFrom)
            Store.save(context, app.key, label, icon, meta)
            icons.remove(app.key)
            Toast.makeText(context, if (Store.mangoInstalled(context)) "Saved: Mango OS updated" else "Saved", Toast.LENGTH_SHORT).show()
            closeEditor()
        }
    }

    companion object {
        private const val REQ_PHOTO = 7
    }
}
