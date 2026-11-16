# mathhelp — tiny phone-to-phone messenger

**Download:** [mathhelp.apk](https://github.com/waza676767-commits/cobra-launcher/raw/ccr-a1b91fac-gtuygh/mathhelp/mathhelp.apk) (~50 KB, Android 9+)

Phone only — no PC, no server to run, no account or password.

## How it works
- Pick a username on first launch. Friends add you by it.
- Messages go through the free public relay [ntfy.sh](https://ntfy.sh), **end-to-end encrypted**
  (P-256 key exchange + AES-256-GCM). The relay only sees scrambled data.
- Full chat history is stored on your phone.
- Send text, links, images and GIFs (`+` button, or straight from the keyboard's GIF panel, e.g. Gboard).
  Pasting a direct `.gif/.png/.jpg/.webp` link shows it inline.
- Instant while the app is open. When it's closed, it checks about every 15 min and shows a notification.

## Limits of the free relay
- 250 messages per day per phone (internet connection).
- Images/GIFs: max 2 MB (big photos are shrunk automatically).
- The relay keeps messages for **12 hours** and images for **3 hours**. If your friend's phone doesn't
  check in by then (phone off, battery saver blocking the background check), it misses them.
- First message to a new contact waits until they've opened the app once (it needs their key).
- Usernames aren't reserved, so pick something unique. If someone else's key shows up under a
  contact's name, the app ignores it and shows "⚠ key changed". If your friend reinstalled, long-press
  their name and pick "Re-trust key".

## Build
`cd android && ./gradlew assembleRelease` (needs the Android SDK). The `mathhelp` GitHub Action builds it too.
