# Msg — tiny, fast messenger

- **Server** (`server/`): Rust (axum + SQLite). One ~3 MB binary, no setup.
- **Android app** (`android/`, prebuilt: `tinymsg.apk`, ~37 KB): plain native Android views, zero libraries.

## Features
- Make an account with any username + password
- Add people by username, chat 1:1
- Send text; links are clickable
- Send images and GIFs (`+` button, or straight from the keyboard's GIF/sticker panel, e.g. Gboard)
- Pasting a direct `.gif/.png/.jpg/.webp` link shows it inline
- Instant delivery (long-polling), unread counts, scroll up for older messages
- Tap an image for fullscreen, long-press a message to copy it

## Run the server
You need the server running somewhere both phones can reach: a cheap VPS, or a PC on the same Wi-Fi.

```sh
cd server
cargo build --release
PORT=8080 DATA=./data ./target/release/msgd
```
Open port 8080 in the firewall. Everything is stored in `DATA/` (`msg.db` + `media/`).

## Install the app
Copy `tinymsg.apk` to the phone and open it (allow "install unknown apps").
On first launch enter the server address (e.g. `203.0.113.5:8080`), pick a username + password, and tap **Create account**.

Build it yourself: `cd android && ./gradlew assembleRelease` (needs the Android SDK).
The GitHub Action `messenger` also builds both the APK and the server binary.

## Notes
- Plain `http://` is unencrypted. For real use, put the server behind HTTPS
  (e.g. Caddy: `caddy reverse-proxy --from chat.example.com --to :8080`) and use `https://chat.example.com` in the app.
- Messages arrive while the app is open; there are no push notifications when it's closed (that would need Google Firebase or a battery-hungry background service).

## API (for other clients)
`Authorization: Bearer <token>` on everything except register/login.

| Method | Path | Body / query | Returns |
|---|---|---|---|
| POST | `/register`, `/login` | `{user, pass}` | `{token, user}` |
| GET | `/contacts` | | `[{user, last}]` |
| POST | `/contacts` | `{user}` | `{user}` |
| POST | `/send` | `{to, text, media?}` | message |
| GET | `/history` | `?with=&before=&limit=` | `[message]` |
| GET | `/poll` | `?since=<id>` (waits ≤25 s) | `{last, msgs}` |
| POST | `/upload` | raw image bytes + `Content-Type` | `{id}` |
| GET | `/m/<id>` | | image bytes |
