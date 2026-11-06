# Aura: an iOS-style Home Screen + Lock Screen for the Galaxy A56

Aura is an Android launcher app (an APK) with a "liquid glass" look inspired by iOS, but with its
own design. It doesn't modify One UI, so you can switch between Aura and One UI Home whenever you want.

## Install

1. Download `Aura.apk` on the phone:
   https://github.com/waza676767-commits/cobra-launcher/raw/claude/samsung-a56-custom-os-at7kcn/android/apk/Aura.apk
2. Open it on the phone and allow "Install unknown apps" for your browser / My Files when asked.
3. Open **Aura** from the app drawer. Tap **Set** on the banner, then choose **Aura** and
   **Set as default** in Samsung's sheet.

A newer APK only installs over the old one if both are signed with the same key. Keep
`aura.jks` and its password private (they're never committed). To make GitHub builds use the key,
add the secrets `AURA_KEYSTORE_B64` (`base64 -w0 aura.jks`) and `AURA_KEY_PASSWORD`. Without them, the
workflow signs with a temporary debug key, and you'd have to uninstall before installing that build.

## Switching between Aura and One UI Home

- **Aura Settings → Switch Home Screen** opens Android's default home app list.
- **Quick Settings tile:** pull down the panel, tap ✎ (edit) and drag **Switch Home** in. One tap
  then takes you to the picker.
- If you're on One UI Home, open **Aura** from the drawer to use it without changing anything.

## What's in it

**Home screen**
- Pages only (no app library), like the iPhone. New apps go on the last page.
- 4×6 grid sized for the A56's tall 20:9 screen. You can change it to 5 columns or 5/7 rows.
- Glass dock (4 or 5 apps) and a **Search** pill that turns into page dots while you swipe.
- Long-press an icon for its menu (app shortcuts, App Info, Hide, Uninstall). Long-press and
  drag to rearrange: icons slide out of the way. Drop one icon on another to make a folder.
  Hold at the screen edge to move to another page.
- Long-press empty space to enter edit mode: **+** adds widgets, ⚙ opens settings,
  **Done** finishes.
- Swipe down anywhere to search apps (or open notifications; you can pick in settings).
- Four icon looks: **Colourful**, **Dark**, **Clear** (frosted glass) and **Tinted**.
- Notification badges on icons (number or dot).

**Widgets**: Weather, Clock (analog / digital), Calendar (day / month) and Batteries in small,
medium and large sizes. You can also add normal Android widgets from any app.

**Lock screen**: a big glass clock with the date, small widgets (battery, weather, alarm, date),
your notifications as glass cards (swipe sideways to clear, tap a stack to expand), music
controls, a flashlight button and a camera button. Swipe up to unlock. Aura's lock screen sits on top of
One UI's lock, so your PIN / fingerprint still protects the phone.

**Settings** (the *Aura Settings* app): appearance (light / dark / automatic), 8 built-in
wallpapers or your own photo, icon style and size, **GUI Scale** (makes all of Aura bigger or
smaller), grid, dock, gestures, widgets and weather location, lock screen options, hidden apps,
and motion & battery options.

## Battery

- Nothing animates while you're not touching the phone. There's no live wallpaper and no
  background service.
- The glass effect samples a small pre-blurred copy of the wallpaper. It doesn't blur live.
- Clock widgets refresh once a minute and only while visible. Weather updates at most every 30
  minutes and only when a weather widget is showing. It uses network location, not GPS.
- **Follow Power Saving** (on by default) turns on Reduce Motion and Reduce Transparency
  whenever One UI's Power saving is on.
- Dark appearance with the **Midnight** or **Graphite** wallpaper uses the least power on the
  AMOLED screen.

## Lock screen setup

1. Aura Settings → Lock Screen → turn on **Aura Lock Screen**.
2. Allow **Notification Access** so notifications and music show up.
3. Aura needs to be your Home Screen, or you need to allow **Display over other apps**.

If Android greys out a switch ("Restricted setting"), open
**App info → ⋮ (top-right) → Allow restricted settings** and try again. Android does this for
apps installed from an APK file.

## Building it yourself

```
cd android
AURA_KEY_PASSWORD=… ./gradlew assembleRelease   # key at app/aura.jks → app/build/outputs/apk/release/
```

You need JDK 17+ and the Android SDK (platform 35). Kotlin, minSdk 31, targetSdk 35. The only
library is AndroidX Core.
