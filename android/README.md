# Mango OS: an iOS-style Home Screen + Lock Screen for the Galaxy A56

Mango OS is an Android launcher app (an APK) with a "liquid glass" look inspired by iOS. It doesn't
modify One UI, so you can switch between Mango OS and One UI Home whenever you want.

## Install

1. Download `MangoOS.apk` on the phone:
   https://github.com/waza676767-commits/cobra-launcher/raw/claude/samsung-a56-custom-os-at7kcn/android/apk/MangoOS.apk
2. If Samsung blocks the install, it's **Auto Blocker**: Settings › Security and privacy ›
   Auto Blocker → off, install, then turn it back on. If Google Play Protect asks, tap
   **More details › Install anyway**. Both appear for any APK that isn't from an app store.
3. Open **Mango OS** from the app drawer. Tap **Set** on the banner, then choose **Mango OS** and
   **Set as default** in Samsung's sheet.

If you had the earlier test version (**Aura**), uninstall it: Mango OS is a separate app.

A newer APK only installs over the old one if both are signed with the same key. Keep
`mango.jks` and its password private (they're never committed). To make GitHub builds use the key,
add the secrets `MANGO_KEYSTORE_B64` (`base64 -w0 mango.jks`) and `MANGO_KEY_PASSWORD`. Without them,
the workflow signs with a temporary debug key, and you'd have to uninstall before installing that build.

## Switching between Mango OS and One UI Home

- **Mango Settings → Switch Home Screen** opens Android's default home app list.
- **Quick Settings tile:** pull down the panel, tap ✎ (edit) and drag **Switch Home** in. One tap
  then takes you to the picker.
- If you're on One UI Home, open **Mango OS** from the drawer to use it without changing anything.

## What's in it

**Home screen**
- iPhone-style pages, with the **App Library** after the last page: a search bar and every app
  sorted into groups (Suggestions, Recently Added, Social, Entertainment, Productivity…).
- 4×6 grid sized for the A56's tall 20:9 screen. You can change it to 5 columns or 5/7 rows.
- Glass dock (4 or 5 apps) and a **Search** pill that turns into page dots while you swipe.
- Long-press an icon for its menu (app shortcuts, App Info, Hide, Uninstall). Long-press and
  drag to rearrange: icons slide out of the way. Drop one icon on another to make a folder.
  Hold at the screen edge to move to another page.
- Long-press empty space to enter edit mode: **+** adds widgets, ⚙ opens settings,
  **Done** finishes.
- Swipe down anywhere to search apps.
- Four icon looks: **Colourful**, **Dark**, **Clear** (frosted glass) and **Tinted**.
- Notification badges on icons (number or dot).

**Widgets**: Weather, Clock (analog / digital), Calendar (day / month) and Batteries in small,
medium and large sizes. You can also add normal Android widgets from any app.

**Lock screen**: a big glass clock with the date, small widgets (battery, weather, alarm, date),
your notifications as glass cards (swipe sideways to clear, tap a stack to expand), music
controls, a flashlight button and a camera button.

**Settings** (the *Mango Settings* app) slides up as an iOS-style sheet with search:
appearance (light / dark / automatic), 9 built-in wallpapers or your own photo, icon style and
size, **GUI Scale** (makes all of Mango OS bigger or smaller), grid, dock, gestures, widgets and
weather location, lock screen options, hidden apps, and motion & battery options.

## Smooth and light on battery

- The screen runs at 120 Hz while something moves and drops back when everything is still.
- Folders, menus and search blur the home screen with a single still picture, taken once, instead of
  blurring live on every frame.
- The Mango wallpaper is also set as the phone's wallpaper (Settings › Wallpaper › Use as Phone
  Wallpaper). This stops the flash of another wallpaper when you close an app, and Android draws it
  for free.
- Nothing runs in the background: no service, no timers. Clock widgets refresh once a minute and
  only while visible. Weather updates at most every 30 minutes and only when a weather widget is
  showing. It uses network location, not GPS.
- **Follow Power Saving** (on by default) turns on Reduce Motion and Reduce Transparency
  whenever One UI's Power saving is on.

## Lock screen setup

1. Mango Settings → Lock Screen → turn on **Mango Lock Screen**. Mango OS must be your Home Screen.
2. Allow **Notification Access** so notifications and music show up.

How unlocking works: Mango's lock screen sits on top of One UI's lock. If your phone has a PIN or
pattern, Android always shows One UI's keypad after you swipe up. No app is allowed to skip it.
The fast way in is to **touch the fingerprint sensor while Mango's lock screen is showing**: with
**Open on Fingerprint** on (the default), Mango opens straight away, with no swipe and no One UI
screen.

If Android greys out the Notification Access switch ("Restricted setting"), open
**App info → ⋮ (top-right) → Allow restricted settings** and try again. Android does this for
apps installed from an APK file.

## Building it yourself

```
cd android
MANGO_KEY_PASSWORD=… ./gradlew assembleRelease   # key at app/mango.jks → app/build/outputs/apk/release/
```

You need JDK 17+ and the Android SDK (platform 35). Kotlin, minSdk 31, targetSdk 35. The only
library is AndroidX Core.
