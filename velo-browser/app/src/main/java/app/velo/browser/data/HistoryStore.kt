package app.velo.browser.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

data class HistoryEntry(val id: Long, val url: String, val title: String, val time: Long)

/** Browsing history in a small SQLite database. All calls block, so use them off the main thread. */
class HistoryStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "history.db", null, 1) {

    /** Increases after every change so open screens know to reload. */
    var revision by mutableIntStateOf(0)
        private set

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE history (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT NOT NULL, " +
                "title TEXT NOT NULL, time INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX history_time ON history(time)")
        db.execSQL("CREATE INDEX history_url ON history(url)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /** Records a visit. A repeat of the newest entry only refreshes its time and title. */
    @Synchronized
    fun record(url: String, title: String, now: Long = System.currentTimeMillis()) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        val db = writableDatabase
        var lastId = -1L
        var lastUrl: String? = null
        db.rawQuery("SELECT id, url FROM history ORDER BY time DESC, id DESC LIMIT 1", null).use { c ->
            if (c.moveToFirst()) {
                lastId = c.getLong(0)
                lastUrl = c.getString(1)
            }
        }
        val values = ContentValues().apply {
            put("title", title.ifBlank { url })
            put("time", now)
        }
        if (lastUrl == url) {
            db.update("history", values, "id = ?", arrayOf(lastId.toString()))
        } else {
            values.put("url", url)
            db.insert("history", null, values)
        }
        revision++
    }

    /** Fills in the title once the page has reported one. */
    @Synchronized
    fun updateTitle(url: String, title: String) {
        if (title.isBlank()) return
        val db = writableDatabase
        db.execSQL(
            "UPDATE history SET title = ? WHERE id = (SELECT id FROM history WHERE url = ? " +
                "ORDER BY time DESC LIMIT 1)",
            arrayOf(title, url)
        )
        revision++
    }

    @Synchronized
    fun query(filter: String = "", limit: Int = 300, offset: Int = 0): List<HistoryEntry> {
        val out = ArrayList<HistoryEntry>()
        val f = filter.trim()
        val cursor = if (f.isEmpty()) {
            readableDatabase.rawQuery(
                "SELECT id, url, title, time FROM history ORDER BY time DESC, id DESC LIMIT ? OFFSET ?",
                arrayOf(limit.toString(), offset.toString())
            )
        } else {
            val like = "%" + f.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            readableDatabase.rawQuery(
                "SELECT id, url, title, time FROM history WHERE url LIKE ? ESCAPE '\\' " +
                    "OR title LIKE ? ESCAPE '\\' ORDER BY time DESC, id DESC LIMIT ? OFFSET ?",
                arrayOf(like, like, limit.toString(), offset.toString())
            )
        }
        cursor.use { c ->
            while (c.moveToNext()) {
                out += HistoryEntry(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3))
            }
        }
        return out
    }

    /** Most recent distinct pages matching what the user is typing. */
    @Synchronized
    fun suggest(text: String, limit: Int = 5): List<HistoryEntry> {
        val seen = HashSet<String>()
        return query(text, limit * 6).filter { seen.add(it.url) }.take(limit)
    }

    /** Most recent distinct pages, newest first. */
    @Synchronized
    fun recentDistinct(limit: Int): List<HistoryEntry> {
        val seen = HashSet<String>()
        return query("", limit * 8).filter { seen.add(it.url) }.take(limit)
    }

    @Synchronized
    fun delete(id: Long) {
        writableDatabase.delete("history", "id = ?", arrayOf(id.toString()))
        revision++
    }

    /** Removes everything visited at or after [time]. */
    @Synchronized
    fun deleteSince(time: Long) {
        writableDatabase.delete("history", "time >= ?", arrayOf(time.toString()))
        revision++
    }

    @Synchronized
    fun clear() {
        writableDatabase.delete("history", null, null)
        revision++
    }

    @Synchronized
    fun count(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM history", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
}
