# mathhelp — tiny phone-to-phone messenger

**Download:** [mathhelp.apk](https://github.com/waza676767-commits/cobra-launcher/raw/ccr-a1b91fac-gtuygh/mathhelp/mathhelp.apk) (~55 KB, Android 9+)

Phone only — no PC, no server to run, no account or password.

## How it works
- Pick a username on first launch.
- **Friends:** type someone's username → **Add friend** → they get a friend request → once they accept, you can chat.
  Only friends can message you; anyone else is ignored.
- Messages go through the free public relay [ntfy.sh](https://ntfy.sh), **end-to-end encrypted**
  (P-256 key exchange + AES-256-GCM). The relay only sees scrambled data.
- Full chat history is stored on your phone.
- Send text, links, images and GIFs (`+` button, or straight from the keyboard's GIF panel, e.g. Gboard).
  Pasting a direct `.gif/.png/.jpg/.webp` link shows it inline.
- Instant, even with the app closed: a small background connection stays open (you'll see a quiet
  "Connected" notification — long-press it to hide it). If Android kills it, a 15-min check takes over.

## 24/7 backup on GitHub
A GitHub Action on `main` (`.github/workflows/mathhelp-backup.yml`) runs every ~15 min and copies new
messages and images from the relay into `mathhelp-archive/` — still end-to-end encrypted. If a phone was off
for longer than the relay keeps things, the app fetches what it missed from there. Images are kept 30 days.

## Limits of the free relay
- 250 messages per day per phone (internet connection).
- Images/GIFs: max 2 MB (big photos are shrunk automatically).
- The relay itself keeps messages 12 h and images 3 h; the GitHub backup covers longer gaps
  (GitHub runs it roughly every 15 min, sometimes later when GitHub is busy).
- Usernames aren't reserved, so pick something unique. Messages from anyone who isn't your accepted
  friend (or from a different phone/key using a friend's name) are ignored. If a friend reinstalls,
  they show up as a new friend request — accept it to keep chatting.

## If it crashes
Open it again: it shows the error with a **Copy** button. Paste that to the developer.

## Build
`cd android && ./gradlew assembleRelease` (needs the Android SDK). The `mathhelp` GitHub Action builds it too.
