package dev.mango.os.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Something that sits on a home screen page or in the dock. */
sealed class Item(val id: Long) {
    open val spanX: Int get() = 1
    open val spanY: Int get() = 1
}

class AppItem(id: Long, val key: String) : Item(id)

class FolderItem(id: Long, var name: String, val apps: MutableList<AppItem>) : Item(id)

/** kind: weather | clock | calendar | battery | android (a normal Android widget) */
class WidgetItem(
    id: Long,
    val kind: String,
    var sx: Int,
    var sy: Int,
    var appWidgetId: Int = -1,
    var provider: String? = null
) : Item(id) {
    override val spanX: Int get() = sx
    override val spanY: Int get() = sy
}

class Placement(val item: Item, val x: Int, val y: Int, val sx: Int, val sy: Int)

/**
 * iOS-style flow layout: a page is an ordered list and items fill the grid left-to-right,
 * top-to-bottom, each at the first spot where it fits. Two-wide widgets snap to even columns.
 */
object Packer {
    fun pack(items: List<Item>, cols: Int, rows: Int, out: MutableList<Placement>, overflow: MutableList<Item>) {
        val occ = Array(rows) { BooleanArray(cols) }
        for (item in items) {
            val sx = item.spanX.coerceIn(1, cols)
            val sy = item.spanY.coerceIn(1, rows)
            var done = false
            var y = 0
            while (y <= rows - sy && !done) {
                var x = 0
                while (x <= cols - sx && !done) {
                    val aligned = sx == 1 || x % 2 == 0 || x + sx == cols
                    if (aligned && fits(occ, x, y, sx, sy)) {
                        for (yy in y until y + sy) for (xx in x until x + sx) occ[yy][xx] = true
                        out.add(Placement(item, x, y, sx, sy))
                        done = true
                    }
                    x++
                }
                y++
            }
            if (!done) overflow.add(item)
        }
    }

    private fun fits(occ: Array<BooleanArray>, x: Int, y: Int, sx: Int, sy: Int): Boolean {
        for (yy in y until y + sy) for (xx in x until x + sx) if (occ[yy][xx]) return false
        return true
    }
}

class HomeLayout {
    val pages = ArrayList<MutableList<Item>>()
    val dock = ArrayList<Item>()
    var nextId = 1L

    fun newId() = nextId++

    fun forEachApp(block: (AppItem) -> Unit) {
        fun visit(list: List<Item>) {
            for (it in list) when (it) {
                is AppItem -> block(it)
                is FolderItem -> it.apps.forEach(block)
                else -> {}
            }
        }
        pages.forEach { visit(it) }
        visit(dock)
    }

    fun appKeys(): HashSet<String> {
        val set = HashSet<String>()
        forEachApp { set.add(it.key) }
        return set
    }

    fun widgets(): List<WidgetItem> = pages.flatMap { p -> p.filterIsInstance<WidgetItem>() }

    /** Removes every icon of [key]; folders left with one app turn back into that app. */
    fun removeApp(key: String): Boolean {
        var changed = false
        fun clean(list: MutableList<Item>) {
            val it = list.listIterator()
            while (it.hasNext()) {
                when (val item = it.next()) {
                    is AppItem -> if (item.key == key) { it.remove(); changed = true }
                    is FolderItem -> {
                        if (item.apps.removeAll { a -> a.key == key }) changed = true
                        when (item.apps.size) {
                            0 -> it.remove()
                            1 -> it.set(item.apps[0])
                        }
                    }
                    else -> {}
                }
            }
        }
        pages.forEach { clean(it) }
        clean(dock)
        return changed
    }

    /** Pushes items that don't fit onto the next page (creating pages as needed). */
    fun reflow(cols: Int, rows: Int, dropEmpty: Boolean, keepPage: Int = -1) {
        if (pages.isEmpty()) pages.add(ArrayList())
        val placed = ArrayList<Placement>()
        val overflow = ArrayList<Item>()
        var i = 0
        while (i < pages.size) {
            placed.clear(); overflow.clear()
            Packer.pack(pages[i], cols, rows, placed, overflow)
            if (overflow.isNotEmpty()) {
                pages[i].removeAll(overflow.toSet())
                if (i + 1 >= pages.size) pages.add(ArrayList())
                pages[i + 1].addAll(0, overflow)
            }
            i++
        }
        if (dropEmpty) {
            var idx = pages.size - 1
            while (idx >= 0 && pages.size > 1) {
                if (pages[idx].isEmpty() && idx != keepPage) pages.removeAt(idx)
                idx--
            }
        }
    }

    fun toJson(): String {
        val o = JSONObject()
        o.put("v", 1)
        o.put("nextId", nextId)
        val ps = JSONArray()
        pages.forEach { p -> ps.put(listJson(p)) }
        o.put("pages", ps)
        o.put("dock", listJson(dock))
        return o.toString()
    }

    private fun listJson(list: List<Item>): JSONArray {
        val a = JSONArray()
        for (it in list) a.put(itemJson(it))
        return a
    }

    private fun itemJson(it: Item): JSONObject {
        val o = JSONObject().put("id", it.id)
        when (it) {
            is AppItem -> o.put("t", "app").put("key", it.key)
            is FolderItem -> {
                o.put("t", "folder").put("name", it.name)
                val a = JSONArray()
                it.apps.forEach { app -> a.put(JSONObject().put("id", app.id).put("key", app.key)) }
                o.put("apps", a)
            }
            is WidgetItem -> {
                o.put("t", "widget").put("kind", it.kind).put("sx", it.sx).put("sy", it.sy)
                    .put("awid", it.appWidgetId)
                it.provider?.let { p -> o.put("provider", p) }
            }
        }
        return o
    }

    companion object {
        fun file(ctx: Context) = File(ctx.filesDir, "layout.json")

        fun load(ctx: Context): HomeLayout? {
            val f = file(ctx)
            if (!f.exists()) return null
            return try {
                parse(f.readText())
            } catch (e: Exception) {
                null
            }
        }

        fun parse(s: String): HomeLayout {
            val o = JSONObject(s)
            val l = HomeLayout()
            l.nextId = o.optLong("nextId", 1L)
            val ps = o.optJSONArray("pages") ?: JSONArray()
            for (i in 0 until ps.length()) l.pages.add(parseList(ps.getJSONArray(i)))
            o.optJSONArray("dock")?.let { l.dock.addAll(parseList(it)) }
            var max = 0L
            fun bump(id: Long) { if (id > max) max = id }
            l.pages.forEach { p -> p.forEach { bump(it.id); if (it is FolderItem) it.apps.forEach { a -> bump(a.id) } } }
            l.dock.forEach { bump(it.id); if (it is FolderItem) it.apps.forEach { a -> bump(a.id) } }
            if (l.nextId <= max) l.nextId = max + 1
            return l
        }

        private fun parseList(a: JSONArray): MutableList<Item> {
            val out = ArrayList<Item>()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val id = o.getLong("id")
                when (o.optString("t")) {
                    "app" -> out.add(AppItem(id, o.getString("key")))
                    "folder" -> {
                        val apps = ArrayList<AppItem>()
                        val arr = o.optJSONArray("apps") ?: JSONArray()
                        for (j in 0 until arr.length()) {
                            val ao = arr.getJSONObject(j)
                            apps.add(AppItem(ao.getLong("id"), ao.getString("key")))
                        }
                        if (apps.isNotEmpty()) out.add(FolderItem(id, o.optString("name", "Folder"), apps))
                    }
                    "widget" -> out.add(
                        WidgetItem(
                            id, o.getString("kind"), o.optInt("sx", 2), o.optInt("sy", 2),
                            o.optInt("awid", -1), if (o.has("provider")) o.getString("provider") else null
                        )
                    )
                }
            }
            return out
        }
    }
}
