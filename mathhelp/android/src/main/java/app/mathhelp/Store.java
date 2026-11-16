package app.mathhelp;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;

/** Everything lives on the phone: identity, contacts, full chat history, media files. */
final class Store {
    static SQLiteDatabase db;
    static SharedPreferences prefs;
    static File mediaDir;
    static String user, pub;
    private static PrivateKey priv;

    static final int SENT = 0, PENDING = 1;
    /** Contact states: friends, I sent a request, they sent me a request. */
    static final int FRIEND = 0, OUT = 1, IN = 2;

    static final class Contact {
        String name, pub;
        long last;
        int unread, state;
        boolean bad, pending;
    }

    static final class Msg {
        long id, ts;
        String peer, text, media, mime, cid;
        boolean loading;
        boolean mine;
        int state;
    }

    static synchronized void init(Context c) {
        if (db != null) return;
        Context app = c.getApplicationContext();
        prefs = app.getSharedPreferences("s", Context.MODE_PRIVATE);
        mediaDir = new File(app.getFilesDir(), "media");
        mediaDir.mkdirs();
        db = new SQLiteOpenHelper(app, "mathhelp.db", null, 3) {
            @Override public void onConfigure(SQLiteDatabase d) { d.enableWriteAheadLogging(); }
            @Override public void onCreate(SQLiteDatabase d) {
                d.execSQL("CREATE TABLE contacts(name TEXT PRIMARY KEY COLLATE NOCASE, pub TEXT, last INTEGER NOT NULL DEFAULT 0," +
                        " unread INTEGER NOT NULL DEFAULT 0, bad INTEGER NOT NULL DEFAULT 0, state INTEGER NOT NULL DEFAULT 0)");
                d.execSQL("CREATE TABLE msgs(_id INTEGER PRIMARY KEY AUTOINCREMENT, peer TEXT NOT NULL COLLATE NOCASE," +
                        " mine INTEGER NOT NULL, ts INTEGER NOT NULL, text TEXT NOT NULL, media TEXT, mime TEXT," +
                        " state INTEGER NOT NULL, cid TEXT UNIQUE, fetch TEXT)");
                d.execSQL("CREATE INDEX msgs_peer ON msgs(peer, _id)");
                d.execSQL("CREATE TABLE seen(id TEXT PRIMARY KEY, t INTEGER NOT NULL) WITHOUT ROWID");
            }
            @Override public void onUpgrade(SQLiteDatabase d, int a, int b) {
                if (a < 2) d.execSQL("ALTER TABLE msgs ADD COLUMN fetch TEXT");
                if (a < 3) {
                    d.execSQL("ALTER TABLE contacts ADD COLUMN state INTEGER NOT NULL DEFAULT 0");
                    d.execSQL("UPDATE contacts SET state=1 WHERE pub IS NULL"); // never answered: still a request
                }
            }
        }.getWritableDatabase();
        user = prefs.getString("user", null);
        pub = prefs.getString("pub", null);
        Img.init(app);
    }

    static boolean hasUser() { return user != null; }

    static void createUser(String name) throws Exception {
        KeyPair kp = Crypto.generate();
        String p = Crypto.b64(kp.getPublic().getEncoded()), k = Crypto.b64(kp.getPrivate().getEncoded());
        prefs.edit().putString("user", name).putString("pub", p).putString("priv", k).commit();
        user = name; pub = p; priv = null;
    }

    static synchronized PrivateKey priv() throws Exception {
        if (priv == null) priv = Crypto.priv(prefs.getString("priv", ""));
        return priv;
    }

    /** Relay resume point: unix seconds, or "all". */
    static String since() { long t = prefs.getLong("since", 0); return t <= 0 ? "all" : String.valueOf(t); }
    static void setSince(long unixSeconds) { prefs.edit().putLong("since", unixSeconds).apply(); }

    /** Returns true the first time a relay message id is seen. */
    static boolean markSeen(String id) {
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("t", System.currentTimeMillis());
        return db.insertWithOnConflict("seen", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1;
    }

    static void unmarkSeen(String id) { db.delete("seen", "id=?", new String[]{id}); }

    /** Kept 40 days: the GitHub backup can replay messages up to ~30 days old. */
    static void pruneSeen() {
        db.delete("seen", "t<?", new String[]{String.valueOf(System.currentTimeMillis() - 40 * 86400_000L)});
    }

    // ---------- contacts ----------

    static Contact contact(String name) {
        try (Cursor c = db.rawQuery("SELECT name,pub,last,unread,bad,state FROM contacts WHERE name=?", new String[]{name})) {
            if (!c.moveToFirst()) return null;
            return readContact(c);
        }
    }

    private static Contact readContact(Cursor c) {
        Contact k = new Contact();
        k.name = c.getString(0); k.pub = c.getString(1); k.last = c.getLong(2);
        k.unread = c.getInt(3); k.bad = c.getInt(4) != 0; k.state = c.getInt(5);
        return k;
    }

    static List<Contact> contacts() {
        List<Contact> r = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT name,pub,last,unread,bad,state," +
                " EXISTS(SELECT 1 FROM msgs m WHERE m.peer=contacts.name AND m.state=1)" +
                " FROM contacts ORDER BY state=2 DESC, last DESC, name", null)) {
            while (c.moveToNext()) { Contact k = readContact(c); k.pending = c.getInt(6) != 0; r.add(k); }
        }
        return r;
    }

    static void addContact(String name, String pubKey, int state) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("pub", pubKey);
        v.put("state", state);
        v.put("last", System.currentTimeMillis());
        db.insertWithOnConflict("contacts", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    static void setPub(String name, String pubKey) {
        ContentValues v = new ContentValues();
        v.put("pub", pubKey);
        v.put("bad", 0);
        db.update("contacts", v, "name=?", new String[]{name});
    }

    static void setState(String name, int state) {
        db.execSQL("UPDATE contacts SET state=?, last=? WHERE name=?", new Object[]{state, System.currentTimeMillis(), name});
    }

    static void markBad(String name) { db.execSQL("UPDATE contacts SET bad=1 WHERE name=?", new Object[]{name}); }
    static void clearUnread(String name) { db.execSQL("UPDATE contacts SET unread=0 WHERE name=?", new Object[]{name}); }

    static void deleteContact(String name) {
        for (Msg m : messages(name, Long.MAX_VALUE, 1_000_000)) if (m.media != null) new File(m.media).delete();
        db.delete("msgs", "peer=?", new String[]{name});
        db.delete("contacts", "name=?", new String[]{name});
    }

    // ---------- messages ----------

    /** Returns row id, or -1 if this cid was already stored. */
    static long insertMsg(String peer, boolean mine, String text, String media, String mime, int state, String cid) {
        ContentValues v = new ContentValues();
        long now = System.currentTimeMillis();
        v.put("peer", peer); v.put("mine", mine ? 1 : 0); v.put("ts", now);
        v.put("text", text == null ? "" : text); v.put("media", media); v.put("mime", mime);
        v.put("state", state); v.put("cid", cid);
        long id = db.insertWithOnConflict("msgs", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        if (id != -1) {
            db.execSQL("UPDATE contacts SET last=?, unread=unread+? WHERE name=?",
                    new Object[]{now, (!mine && !peer.equalsIgnoreCase(ChatActivity.openPeer)) ? 1 : 0, peer});
        }
        return id;
    }

    static void setFetch(long id, String f) { db.execSQL("UPDATE msgs SET fetch=? WHERE _id=?", new Object[]{f, id}); }

    static void setMedia(long id, String path, String mime) {
        db.execSQL("UPDATE msgs SET media=?, mime=?, fetch=NULL WHERE _id=?", new Object[]{path, mime, id});
    }

    static void expireMedia(long id, String note) {
        db.execSQL("UPDATE msgs SET fetch=NULL, text=CASE WHEN text='' THEN ? ELSE text||char(10)||? END WHERE _id=?",
                new Object[]{note, note, id});
    }

    static long msgTime(long id) {
        try (Cursor c = db.rawQuery("SELECT ts FROM msgs WHERE _id=?", new String[]{String.valueOf(id)})) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }

    static String fetchOf(long id) {
        try (Cursor c = db.rawQuery("SELECT fetch FROM msgs WHERE _id=?", new String[]{String.valueOf(id)})) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    static List<long[]> fetchRows() {
        List<long[]> r = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT _id FROM msgs WHERE fetch IS NOT NULL ORDER BY _id DESC LIMIT 50", null)) {
            while (c.moveToNext()) r.add(new long[]{c.getLong(0)});
        }
        return r;
    }

    static void markSent(long id) { db.execSQL("UPDATE msgs SET state=0 WHERE _id=?", new Object[]{id}); }

    private static Msg readMsg(Cursor c) {
        Msg m = new Msg();
        m.id = c.getLong(0); m.peer = c.getString(1); m.mine = c.getInt(2) != 0; m.ts = c.getLong(3);
        m.text = c.getString(4); m.media = c.getString(5); m.mime = c.getString(6); m.state = c.getInt(7); m.cid = c.getString(8); m.loading = !c.isNull(9);
        return m;
    }

    private static final String COLS = "_id,peer,mine,ts,text,media,mime,state,cid,fetch";

    /** Up to `limit` messages with id < before, returned oldest first. */
    static List<Msg> messages(String peer, long before, int limit) {
        List<Msg> r = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT " + COLS + " FROM msgs WHERE peer=? AND _id<? ORDER BY _id DESC LIMIT " + limit,
                new String[]{peer, String.valueOf(before)})) {
            while (c.moveToNext()) r.add(0, readMsg(c));
        }
        return r;
    }

    /** All messages with id >= from, oldest first. */
    static List<Msg> messagesFrom(String peer, long from) {
        List<Msg> r = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT " + COLS + " FROM msgs WHERE peer=? AND _id>=? ORDER BY _id",
                new String[]{peer, String.valueOf(from)})) {
            while (c.moveToNext()) r.add(readMsg(c));
        }
        return r;
    }

    static List<Msg> pending(String peer) {
        List<Msg> r = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT " + COLS + " FROM msgs WHERE peer=? AND state=1 ORDER BY _id", new String[]{peer})) {
            while (c.moveToNext()) r.add(readMsg(c));
        }
        return r;
    }

    static List<String> peersWithPending() {
        List<String> r = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT DISTINCT peer FROM msgs WHERE state=1", null)) {
            while (c.moveToNext()) r.add(c.getString(0));
        }
        return r;
    }

    private Store() {}
}
