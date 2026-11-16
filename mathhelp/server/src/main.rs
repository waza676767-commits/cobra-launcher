//! mathhelp — tiny, fast messaging server.
//!
//! Storage: one SQLite file + a directory of uploaded media.
//! Delivery: HTTP long-polling (works through every proxy / mobile network).
//!
//! Env: PORT (default 8080), DATA (default ./data)

use argon2::password_hash::{PasswordHash, PasswordHasher, PasswordVerifier, SaltString};
use argon2::Argon2;
use axum::body::Bytes;
use axum::extract::{DefaultBodyLimit, Path, Query, State};
use axum::http::{header, HeaderMap, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::routing::{get, post};
use axum::{Json, Router};
use rusqlite::{params, Connection, OptionalExtension};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use std::time::{Duration, SystemTime, UNIX_EPOCH};
use tokio::sync::watch;

const MAX_UPLOAD: usize = 20 * 1024 * 1024;
const POLL_WAIT: Duration = Duration::from_secs(25);

struct App {
    db: Mutex<Connection>,
    tick: watch::Sender<i64>,
    media: PathBuf,
}
type S = State<Arc<App>>;
type R<T> = Result<T, (StatusCode, &'static str)>;

fn err(c: StatusCode, m: &'static str) -> (StatusCode, &'static str) {
    (c, m)
}
fn ise<E: std::fmt::Debug>(e: E) -> (StatusCode, &'static str) {
    eprintln!("internal error: {e:?}");
    err(StatusCode::INTERNAL_SERVER_ERROR, "internal error")
}

fn rand_hex(n: usize) -> String {
    let mut b = vec![0u8; n];
    getrandom::getrandom(&mut b).expect("rng");
    b.iter().map(|x| format!("{x:02x}")).collect()
}

fn now() -> i64 {
    SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis() as i64
}

fn valid_name(s: &str) -> bool {
    (2..=24).contains(&s.len())
        && s.bytes().all(|c| c.is_ascii_alphanumeric() || c == b'_' || c == b'.' || c == b'-')
}

fn open_db(path: &std::path::Path) -> Connection {
    let c = Connection::open(path).expect("open db");
    c.execute_batch(
        "PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL; PRAGMA foreign_keys=ON;
         CREATE TABLE IF NOT EXISTS users(id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE COLLATE NOCASE, pass TEXT NOT NULL);
         CREATE TABLE IF NOT EXISTS tokens(token TEXT PRIMARY KEY, uid INTEGER NOT NULL);
         CREATE TABLE IF NOT EXISTS contacts(uid INTEGER NOT NULL, cid INTEGER NOT NULL, PRIMARY KEY(uid,cid)) WITHOUT ROWID;
         CREATE TABLE IF NOT EXISTS media(id TEXT PRIMARY KEY, mime TEXT NOT NULL, uid INTEGER NOT NULL) WITHOUT ROWID;
         CREATE TABLE IF NOT EXISTS msgs(id INTEGER PRIMARY KEY AUTOINCREMENT, src INTEGER NOT NULL, dst INTEGER NOT NULL,
             ts INTEGER NOT NULL, text TEXT NOT NULL, media TEXT, mime TEXT);
         CREATE INDEX IF NOT EXISTS msgs_dst ON msgs(dst, id);
         CREATE INDEX IF NOT EXISTS msgs_src ON msgs(src, id);",
    )
    .expect("schema");
    c
}

/// Resolve the bearer token to (user id, user name).
fn auth(app: &App, h: &HeaderMap) -> R<(i64, String)> {
    let t = h
        .get(header::AUTHORIZATION)
        .and_then(|v| v.to_str().ok())
        .map(|v| v.trim_start_matches("Bearer ").trim())
        .ok_or(err(StatusCode::UNAUTHORIZED, "no token"))?;
    let db = app.db.lock().unwrap();
    db.query_row(
        "SELECT u.id, u.name FROM tokens t JOIN users u ON u.id=t.uid WHERE t.token=?1",
        [t],
        |r| Ok((r.get(0)?, r.get(1)?)),
    )
    .optional()
    .map_err(ise)?
    .ok_or(err(StatusCode::UNAUTHORIZED, "bad token"))
}

fn user_id(db: &Connection, name: &str) -> R<i64> {
    db.query_row("SELECT id FROM users WHERE name=?1", [name], |r| r.get(0))
        .optional()
        .map_err(ise)?
        .ok_or(err(StatusCode::NOT_FOUND, "no such user"))
}

#[derive(Serialize)]
struct Msg {
    id: i64,
    from: String,
    to: String,
    ts: i64,
    text: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    media: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    mime: Option<String>,
}

const MSG_SELECT: &str = "SELECT m.id, a.name, b.name, m.ts, m.text, m.media, m.mime
    FROM msgs m JOIN users a ON a.id=m.src JOIN users b ON b.id=m.dst ";

fn row_msg(r: &rusqlite::Row) -> rusqlite::Result<Msg> {
    Ok(Msg {
        id: r.get(0)?,
        from: r.get(1)?,
        to: r.get(2)?,
        ts: r.get(3)?,
        text: r.get(4)?,
        media: r.get(5)?,
        mime: r.get(6)?,
    })
}

// ---------- accounts ----------

#[derive(Deserialize)]
struct Creds {
    user: String,
    pass: String,
}

fn new_token(db: &Connection, uid: i64) -> R<String> {
    let t = rand_hex(24);
    db.execute("INSERT INTO tokens(token,uid) VALUES(?1,?2)", params![t, uid]).map_err(ise)?;
    Ok(t)
}

async fn register(State(app): S, Json(c): Json<Creds>) -> R<Json<Value>> {
    let user = c.user.trim().to_string();
    if !valid_name(&user) {
        return Err(err(StatusCode::BAD_REQUEST, "username: 2-24 chars, letters/digits/_.-"));
    }
    if c.pass.len() < 4 {
        return Err(err(StatusCode::BAD_REQUEST, "password too short (min 4)"));
    }
    let hash = tokio::task::spawn_blocking(move || {
        let salt = SaltString::encode_b64(rand_hex(16).as_bytes()).unwrap();
        Argon2::default().hash_password(c.pass.as_bytes(), &salt).map(|h| h.to_string())
    })
    .await
    .map_err(ise)?
    .map_err(ise)?;
    let db = app.db.lock().unwrap();
    let n = db
        .execute("INSERT OR IGNORE INTO users(name,pass) VALUES(?1,?2)", params![user, hash])
        .map_err(ise)?;
    if n == 0 {
        return Err(err(StatusCode::CONFLICT, "username taken"));
    }
    let uid = db.last_insert_rowid();
    let token = new_token(&db, uid)?;
    Ok(Json(json!({ "token": token, "user": user })))
}

async fn login(State(app): S, Json(c): Json<Creds>) -> R<Json<Value>> {
    let row: Option<(i64, String, String)> = {
        let db = app.db.lock().unwrap();
        db.query_row("SELECT id,name,pass FROM users WHERE name=?1", [c.user.trim()], |r| {
            Ok((r.get(0)?, r.get(1)?, r.get(2)?))
        })
        .optional()
        .map_err(ise)?
    };
    let bad = err(StatusCode::UNAUTHORIZED, "wrong username or password");
    let (uid, name, hash) = row.ok_or(bad)?;
    let ok = tokio::task::spawn_blocking(move || {
        PasswordHash::new(&hash)
            .map(|h| Argon2::default().verify_password(c.pass.as_bytes(), &h).is_ok())
            .unwrap_or(false)
    })
    .await
    .map_err(ise)?;
    if !ok {
        return Err(bad);
    }
    let db = app.db.lock().unwrap();
    let token = new_token(&db, uid)?;
    Ok(Json(json!({ "token": token, "user": name })))
}

// ---------- contacts ----------

#[derive(Deserialize)]
struct AddContact {
    user: String,
}

async fn add_contact(State(app): S, h: HeaderMap, Json(a): Json<AddContact>) -> R<Json<Value>> {
    let (uid, _) = auth(&app, &h)?;
    let db = app.db.lock().unwrap();
    let cid = user_id(&db, a.user.trim())?;
    if cid == uid {
        return Err(err(StatusCode::BAD_REQUEST, "that's you"));
    }
    db.execute("INSERT OR IGNORE INTO contacts(uid,cid) VALUES(?1,?2)", params![uid, cid])
        .map_err(ise)?;
    let name: String =
        db.query_row("SELECT name FROM users WHERE id=?1", [cid], |r| r.get(0)).map_err(ise)?;
    Ok(Json(json!({ "user": name })))
}

/// Contacts = people you added + anyone you've exchanged messages with,
/// most recent conversation first.
async fn contacts(State(app): S, h: HeaderMap) -> R<Json<Value>> {
    let (uid, _) = auth(&app, &h)?;
    let db = app.db.lock().unwrap();
    let mut st = db
        .prepare_cached(
            "WITH peers(pid, last) AS (
                 SELECT cid, 0 FROM contacts WHERE uid=?1
                 UNION ALL SELECT dst, MAX(id) FROM msgs WHERE src=?1 GROUP BY dst
                 UNION ALL SELECT src, MAX(id) FROM msgs WHERE dst=?1 GROUP BY src)
             SELECT u.name, MAX(p.last) l FROM peers p JOIN users u ON u.id=p.pid
             WHERE p.pid<>?1 GROUP BY p.pid ORDER BY l DESC, u.name",
        )
        .map_err(ise)?;
    let list: Vec<Value> = st
        .query_map([uid], |r| Ok(json!({ "user": r.get::<_, String>(0)?, "last": r.get::<_, i64>(1)? })))
        .map_err(ise)?
        .collect::<Result<_, _>>()
        .map_err(ise)?;
    Ok(Json(Value::Array(list)))
}

// ---------- messages ----------

#[derive(Deserialize)]
struct Send {
    to: String,
    #[serde(default)]
    text: String,
    media: Option<String>,
}

async fn send(State(app): S, h: HeaderMap, Json(s): Json<Send>) -> R<Json<Msg>> {
    let (uid, _) = auth(&app, &h)?;
    let text = s.text.trim().to_string();
    if text.is_empty() && s.media.is_none() {
        return Err(err(StatusCode::BAD_REQUEST, "empty message"));
    }
    if text.len() > 16 * 1024 {
        return Err(err(StatusCode::BAD_REQUEST, "message too long"));
    }
    let msg = {
        let db = app.db.lock().unwrap();
        let dst = user_id(&db, s.to.trim())?;
        let mime: Option<String> = match &s.media {
            Some(m) => Some(
                db.query_row("SELECT mime FROM media WHERE id=?1", [m], |r| r.get(0))
                    .optional()
                    .map_err(ise)?
                    .ok_or(err(StatusCode::BAD_REQUEST, "unknown media"))?,
            ),
            None => None,
        };
        db.execute(
            "INSERT INTO msgs(src,dst,ts,text,media,mime) VALUES(?1,?2,?3,?4,?5,?6)",
            params![uid, dst, now(), text, s.media, mime],
        )
        .map_err(ise)?;
        let id = db.last_insert_rowid();
        db.query_row(&format!("{MSG_SELECT} WHERE m.id=?1"), [id], row_msg).map_err(ise)?
    };
    app.tick.send_replace(msg.id);
    Ok(Json(msg))
}

#[derive(Deserialize)]
struct HistQ {
    with: String,
    before: Option<i64>,
    limit: Option<i64>,
}

/// Conversation with one peer, oldest→newest, paged backwards with `before`.
async fn history(State(app): S, h: HeaderMap, Query(q): Query<HistQ>) -> R<Json<Vec<Msg>>> {
    let (uid, _) = auth(&app, &h)?;
    let db = app.db.lock().unwrap();
    let pid = user_id(&db, q.with.trim())?;
    let mut st = db
        .prepare_cached(&format!(
            "{MSG_SELECT} WHERE ((m.src=?1 AND m.dst=?2) OR (m.src=?2 AND m.dst=?1)) AND m.id<?3
             ORDER BY m.id DESC LIMIT ?4"
        ))
        .map_err(ise)?;
    let mut v: Vec<Msg> = st
        .query_map(
            params![uid, pid, q.before.unwrap_or(i64::MAX), q.limit.unwrap_or(60).clamp(1, 500)],
            row_msg,
        )
        .map_err(ise)?
        .collect::<Result<_, _>>()
        .map_err(ise)?;
    v.reverse();
    Ok(Json(v))
}

#[derive(Deserialize)]
struct PollQ {
    since: Option<i64>,
}

/// Long-poll: returns as soon as there is any message to/from you with id > since,
/// or after ~25s with an empty list. Without `since`, returns the current head id.
async fn poll(State(app): S, h: HeaderMap, Query(q): Query<PollQ>) -> R<Json<Value>> {
    let (uid, _) = auth(&app, &h)?;
    let mut rx = app.tick.subscribe();
    let Some(since) = q.since.filter(|s| *s >= 0) else {
        let head = *rx.borrow();
        return Ok(Json(json!({ "last": head, "msgs": [] })));
    };
    let deadline = tokio::time::Instant::now() + POLL_WAIT;
    loop {
        let msgs: Vec<Msg> = {
            let db = app.db.lock().unwrap();
            let mut st = db
                .prepare_cached(&format!(
                    "{MSG_SELECT} WHERE (m.dst=?1 OR m.src=?1) AND m.id>?2 ORDER BY m.id LIMIT 500"
                ))
                .map_err(ise)?;
            let v = st.query_map(params![uid, since], row_msg).map_err(ise)?.collect::<Result<_, _>>();
            v.map_err(ise)?
        };
        if let Some(l) = msgs.last() {
            let last = l.id;
            return Ok(Json(json!({ "last": last, "msgs": msgs })));
        }
        match tokio::time::timeout_at(deadline, rx.changed()).await {
            Ok(Ok(())) => continue,
            _ => return Ok(Json(json!({ "last": since, "msgs": [] }))),
        }
    }
}

// ---------- media ----------

async fn upload(State(app): S, h: HeaderMap, body: Bytes) -> R<Json<Value>> {
    let (uid, _) = auth(&app, &h)?;
    let mime = h
        .get(header::CONTENT_TYPE)
        .and_then(|v| v.to_str().ok())
        .unwrap_or("")
        .to_ascii_lowercase();
    if !mime.starts_with("image/") || mime.len() > 64 {
        return Err(err(StatusCode::UNSUPPORTED_MEDIA_TYPE, "only images/gifs"));
    }
    if body.is_empty() {
        return Err(err(StatusCode::BAD_REQUEST, "empty upload"));
    }
    let id = rand_hex(16);
    tokio::fs::write(app.media.join(&id), &body).await.map_err(ise)?;
    app.db
        .lock()
        .unwrap()
        .execute("INSERT INTO media(id,mime,uid) VALUES(?1,?2,?3)", params![id, mime, uid])
        .map_err(ise)?;
    Ok(Json(json!({ "id": id })))
}

async fn media(State(app): S, h: HeaderMap, Path(id): Path<String>) -> R<Response> {
    auth(&app, &h)?;
    if id.len() != 32 || !id.bytes().all(|c| c.is_ascii_hexdigit()) {
        return Err(err(StatusCode::NOT_FOUND, "not found"));
    }
    let mime: String = app
        .db
        .lock()
        .unwrap()
        .query_row("SELECT mime FROM media WHERE id=?1", [&id], |r| r.get(0))
        .optional()
        .map_err(ise)?
        .ok_or(err(StatusCode::NOT_FOUND, "not found"))?;
    let data = tokio::fs::read(app.media.join(&id)).await.map_err(|_| err(StatusCode::NOT_FOUND, "not found"))?;
    Ok((
        [
            (header::CONTENT_TYPE, mime),
            (header::CACHE_CONTROL, "private, max-age=31536000, immutable".into()),
            (header::X_CONTENT_TYPE_OPTIONS, "nosniff".into()),
        ],
        data,
    )
        .into_response())
}

#[tokio::main]
async fn main() {
    let data = PathBuf::from(std::env::var("DATA").unwrap_or_else(|_| "data".into()));
    let media_dir = data.join("media");
    std::fs::create_dir_all(&media_dir).expect("create data dir");
    let db = open_db(&data.join("msg.db"));
    let head: i64 = db.query_row("SELECT COALESCE(MAX(id),0) FROM msgs", [], |r| r.get(0)).unwrap();
    let app = Arc::new(App { db: Mutex::new(db), tick: watch::channel(head).0, media: media_dir });

    let router = Router::new()
        .route("/register", post(register))
        .route("/login", post(login))
        .route("/contacts", get(contacts).post(add_contact))
        .route("/send", post(send))
        .route("/history", get(history))
        .route("/poll", get(poll))
        .route("/upload", post(upload).layer(DefaultBodyLimit::max(MAX_UPLOAD)))
        .route("/m/{id}", get(media))
        .route("/", get(|| async { "mathhelp ok" }))
        .with_state(app);

    let port = std::env::var("PORT").unwrap_or_else(|_| "8080".into());
    let addr = format!("0.0.0.0:{port}");
    let listener = tokio::net::TcpListener::bind(&addr).await.expect("bind");
    eprintln!("mathhelp listening on {addr}, data in {}", data.display());
    axum::serve(listener, router)
        .with_graceful_shutdown(async {
            let _ = tokio::signal::ctrl_c().await;
        })
        .await
        .unwrap();
}
