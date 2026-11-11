# Velo

A clean, fast Android web browser. Search is DuckDuckGo. Velo is its own Gradle project and is
not part of the launcher build in the repository root.

## What it does

- **Browsing**: tabs with a card switcher, address bar with history matches and DuckDuckGo
  suggestions, find in page, desktop site, downloads, share, long-press menu for links and pictures.
- **Tabs come back**: open tabs are saved as you browse. If Velo is closed or killed, it asks on the
  next start whether to restore them. Settings → General can make it always restore or always start
  fresh.
- **Pinned pages**: pin any page to the start page; long-press a tile to rename or remove it.
- **Ad blocker**: blocks requests to ad and tracker hosts and hides leftover ad boxes. Ships with a
  built-in list and can download Peter Lowe's server list on top. Can be paused per site.
- **Private search**: one switch that keeps searches out of history and suggestions, strips
  tracking tags from links, blocks third-party cookies and sends the Global Privacy Control signal.
- **VPN**: routes everything Velo loads through an HTTP, HTTPS or SOCKS5 server you add, with no
  fall-back to a direct connection. Velo does not include servers; use your provider's, or Tor
  through the Orbot app (preset included). It covers Velo only, not other apps.
- **RAM limit**: Velo estimates its memory use and puts the least recently used background tabs to
  sleep when it goes over the limit you set. Sleeping tabs reload when opened.
- **Assistant**: chat with Claude, ChatGPT or Gemini from any page, using your own account. Paste an
  API key to use Velo's built-in chat (it can read the page you are on), or sign in on the
  provider's own site inside the panel. Keys are encrypted with the Android Keystore.
- **Looks**: light, dark and pure-black themes, any button colour, three shape styles, top or bottom
  toolbar, gradient or photo wallpapers, and start-page widgets (clock, privacy stats, recent pages).
- **Home-screen widgets**: a search bar and a grid of your pinned pages.

## Build

Needs JDK 17 or newer and the Android SDK (platform 37, build-tools 37). Point `local.properties`
at the SDK (`sdk.dir=/path/to/android-sdk`), then:

```bash
./gradlew :app:assembleRelease     # app/build/outputs/apk/release/app-release.apk
./gradlew :app:testDebugUnitTest   # logic tests and screen tests; pictures land in app/build/shots
```

The release build is signed with `keystore/velo-release.jks`, which is committed so that every build
installs as an update over the last one. Replace it with a private key before publishing the app.

## Layout

```
app/src/main/java/app/velo/browser/
├── MainActivity.kt, Browser.kt   activity and the controller the interface talks to
├── browser/                      tabs and WebViews, ad blocker, VPN tunnel
├── ai/                           Claude (Anthropic SDK), ChatGPT and Gemini back ends, chat state
├── data/                         settings, history, pins, saved session, encrypted secrets
├── ui/                           Compose screens, theme and icon set
└── widget/                       home-screen widgets
```

## Things to know

- Pages are drawn by Android System WebView, so page compatibility and security fixes follow the
  WebView version installed on the phone.
- The RAM figure is an estimate: Android does not let an app measure its web-page process, so each
  loaded page is counted at a typical size on top of Velo's own measured memory.
- File downloads are handed to Android's download service, which does not go through the VPN. Velo
  says so in the download prompt while the VPN is on.
- The Anthropic SDK is kept unshrunk in release builds because it relies on runtime reflection;
  this is most of the APK's size.
