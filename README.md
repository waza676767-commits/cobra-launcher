# Abyss Launcher + Cobra Client

Minecraft launcher and client in one IntelliJ project. Black/charcoal UI with white accents,
built from your reference images: collapsible icon rail, centred Launch button with a version
selector, logo fade on launch, and an in-game menu opened with **Right Shift**.

```
CobraLauncher/
├── launcher/        Swing launcher (Java 21) — accounts, installs, mods, packs, analytics, settings
├── common/          shared client core (Java 8 source) — modules, HUD, Right Shift menu, title menu
├── client/          Cobra Client mod for Minecraft 1.21.11 (Fabric + Mixin) — separate Gradle build
└── build-appimage.sh
```

---

## 1. Requirements

Nobara / Fedora:

```bash
sudo dnf install java-21-openjdk-devel java-21-openjdk-jmods git
```

`jmods` is needed by `jlink` for the AppImage. IntelliJ IDEA: open the folder, let Gradle import
`launcher`. For the client, right-click `client/build.gradle` → **Link Gradle Project** (it uses
its own, newer Gradle and Loom for Minecraft 1.21.11).

## 2. Signing in

Click the account chip (top right). Three ways, easiest first:

1. **Use my Prism Launcher account** — no setup. Cobra reads the Minecraft session of the
   account you're signed into in Prism (`~/.local/share/PrismLauncher/accounts.json`, Flatpak and
   PolyMC paths too) and checks it with Mojang. The session lasts about a day; opening Prism
   refreshes it, and Cobra re-reads it on every launch. If it says the session expired: open
   Prism, launch any instance once, try again.
2. **Sign in with Microsoft** — no setup. Your browser opens Microsoft's sign-in page (the one
   Minecraft's own launcher uses). Afterwards you land on a blank page: copy its address
   (Ctrl+L, Ctrl+C) and Cobra picks it up from the clipboard (or paste it into the box).
   The session refreshes itself after that. If you've set your own Azure client ID, the
   loopback browser flow for that app is used instead.
3. **Sign in with a code** — device-code flow; only shown when you've set your own Azure app.

**No account mode** (Settings → Account): skips sign-in completely and plays offline.
Singleplayer only — the Cobra main menu hides Multiplayer in this mode.

### Own Azure app (optional; only needed for "Sign in with a code")

1. <https://portal.azure.com> → App registrations → New registration → **Personal Microsoft
   accounts only**. If it says apps outside a directory are deprecated, create a tenant first
   (Microsoft Entra ID → Manage tenants → Create), switch to it, then register.
2. Authentication → Add a platform → **Mobile and desktop applications** → tick `http://localhost`.
3. **Allow public client flows: Yes** → Save.
4. Copy the Application (client) ID into Settings → Account → Microsoft client ID.
5. Submit the ID at <https://aka.ms/mce-reviewappid>. Until Mojang approves it, sign-in ends in
   a 403.

Tenant errors (AADSTS50194): set `"msTenant": "common"` in
`~/.local/share/CobraLauncher/settings.json`.

## 3. Build and run

```bash
(cd client && ./gradlew build)          # the Cobra Client mod jar (1.21.11)
(cd client && ./gradlew runClient)      # dev Minecraft 1.21.11 with the client loaded
./gradlew :launcher:run                 # run the launcher (bundles the client jar if it's built)
./build-appimage.sh                     # everything + CobraLauncher-x86_64.AppImage
```

`build-appimage.sh` builds the client jar, bundles it inside the launcher jar, jlinks a
trimmed Java 21 runtime and packs an AppImage (~36 MB). Run it with
`./CobraLauncher-x86_64.AppImage`. IntelliJ run configurations for all of this are in `.run/`.

The launcher installs the bundled client jar into the profile's `mods/` folder on every launch,
and auto-installs Fabric API for 1.21.11 (from Modrinth). If the client jar isn't bundled
(launcher built alone), the game still launches, just without Cobra, and the launcher says so.

## 4. Using it

- **Profiles** (bottom of the sidebar) — each profile has a name, a picture and a version, and
  its own mods, texture packs, worlds and options (per version). Click a profile to switch,
  click the active one (or right-click any) to edit or delete it, **+** makes a new one. The
  first profile, *Default*, uses the original `instances/<version>` folders.

- **Home** — Launch button and the profile chip. Minecraft **1.21.11** with Fabric, Fabric API,
  your Modrinth mods and Cobra Client. Top right: a clock in your time zone (looked up from your IP once
  per start, falls back to the system zone) with the date under it. Launching
  fades the logo in over the screen with the install status under it, then fades out when the
  game starts. The launcher minimises while you play unless you turn that off in Settings.
- **Mods / Texture packs** — per version. Installed list with on/off switches and delete, "Add
  files", drag and drop onto the window, or **Browse Modrinth** with one-click install
  (dependencies included). Packs are switched on/off by editing the profile's `options.txt`.
- **Analytics** — play time tracked per session: last 7 days, today, sessions, longest session,
  daily bar chart, split by version. Sessions survive a launcher crash (checkpointed every minute).
- **Settings** — Appearance: **Light mode** (white surfaces, black buttons/text — the in-game
  main menu and Right Shift menu follow it), **Style: Solid / Liquid glass** (clear glass
  that bends the wallpaper at its edges like a lens, bright specular rim, top sheen; it follows
  animated wallpapers frame by frame), **Glass frost** (blur strength: low = clearer), **Wallpaper** (image, GIF or video — MP4/WebM/MKV/MOV —
  behind the launcher and the in-game main menu; animated ones need `ffmpeg`, which Nobara ships),
  **Wallpaper dim**, animations. Game: max RAM, window size, fullscreen, JVM args.
  Account: sign-in, No account mode, offline name, Microsoft client ID. Files: data folder.

Files live in `~/.local/share/CobraLauncher` (`instances/1.21.11`, shared `libraries`, `assets`,
`versions`, `runtimes`, `logs`). Java for the game (Java 21) is downloaded from Mojang; your system
Java isn't used.

## Sign-in options

- **Use my Prism Launcher account**, or **Lunar Client / Dawn Client / Fast Client**: reuses the
  Minecraft session that launcher already has on this computer (it reads the Minecraft token from
  its account files; the token says whose account it is and when it expires). Open that launcher
  and play once if the session expired. Launchers that encrypt their sign-ins can't be shared; the
  message tells you so, and Prism or Microsoft sign-in still work.
- **Sign in with Microsoft / with a code**: needs your own Azure client ID (see above).
- **No account**: offline, singleplayer only.

## App install

`./build-appimage.sh` also installs it as an app: the AppImage goes to `~/Applications/`, and
"Cobra Launcher" appears in your app menu (search "Cobra") with its icon. The launcher keeps that
menu entry pointing at itself if you move the file. Use `./build-appimage.sh --no-install` to skip.

## 5. In-game

Cobra main menu (logo, Singleplayer / Multiplayer, icon bar: Options, Resource Packs, Cobra
settings) and **Right Shift** for the module menu: categories, search, a card per module with a
switch, per-module settings and **Edit HUD** (drag anywhere, scroll to resize, right-click for
settings). **Client** (left rail) holds theme, animations, click particles, the menu key, and blur:
*Blur behind the Cobra menu* on/off, and *Blur other screens* Off / Menus / Menus + inventory.

Settings types: switches, sliders, modes, colours (quick swatches, or click the current colour
for a colour wheel with brightness/opacity), text, buttons, and key binds (click, press a key;
Esc or Backspace clears). Every module has a **Toggle key**. Zoom (`C`), Freelook (`Left Alt`),
Waypoint (`B`) and the menu key (`Right Shift`) are rebindable in the module settings.

**New:** **View Model** (BactroMod-style: pick an item type — All items, Swords, Tools, Blocks,
Bows, Food, Shield, Other items, Empty hand — and set its position X/Y/Z, rotation X/Y/Z and size;
"All items" applies to everything and each type adds its own tweak on top; mirrored for the off
hand; plus fire height), **Hit Color** (colour and strength of the red hit flash), **Visual Tweaks**
(pumpkin blur, and separate switches for lava / water / powder snow / blindness / darkness /
terrain / thick (Nether) / sky fog),
**Block Overlay** (outline colour, see-through fill, rainbow), **Block Info** (HUD: icon + name of the
block you look at, optional id), Custom Crosshair **Inverted color (like vanilla)**, and Zoom now reads
its key directly (C clashes with vanilla's Save Toolbar), lowers mouse sensitivity while zoomed and
keeps the held item unzoomed.

The launcher wallpaper — animated ones too, streamed from the launcher's frame folder — plays on
the Cobra main menu and behind Singleplayer, Multiplayer and the other menus outside a world.

**Launcher icon** (Settings → Appearance): choose any picture as the launcher's icon and logo
(window, taskbar, app menu and the logo inside the launcher); **Reset** brings the Cobra star back.

Modules: Keystrokes, CPS, FPS, Ping, Coordinates, Direction HUD, Armor Status, Reach Display
(only after a hit), Combo Counter, Potion Effects, Boss Bar, Scoreboard, **Memory Usage**,
**Stopwatch / Timer**, **Server Address**, **Item Counter** (arrows, gapples, pearls, splash
potions, held item), **Team View**, Toggle Sprint/Sneak, Motion Blur, Item Model, Fullbright,
Custom Crosshair, Hit Color, No Hurt Camera, Item Physics, Zoom, Freelook, Time Changer, FOV
Modifier, Particle Changer, **Chunk Borders**, **Hitboxes**, **Glint Colorizer**, Waypoints, Chat
Mod, Nick Hider, Screenshot Uploader, BedWars addons, **Hypixel Quickplay** (one-click queues +
AutoTip every 15 minutes, Hypixel only). HUD elements have background colour, rounded corners and
text shadow options.

**Accessories** (launcher sidebar, under Settings): import a skin (64×64, or old 64×32 which is
converted) with Classic/Slim arms, and a cape: 64×32, 64×64 (top half), 32×32 (cape-only), HD
sizes like 128×64 (kept sharp), old 22×17 and OptiFine 46×22 layouts. Cobra Client puts them on your player —
in first/third person, the inventory and the menu head. That part is client-side: only you see
it. **Apply to my Minecraft account** uploads the skin to your real account (needs Microsoft or
Prism sign-in) so everyone sees it; capes can't be uploaded, Mojang only allows capes you own.

**Version:** the launcher and Cobra Client run **Minecraft 1.21.11** (Fabric) only. Profiles made
with older launcher builds switch to 1.21.11 automatically; their worlds, resource packs, server
list (and options + Cobra settings when they came from another 1.21 version) move along. Old mods
stay in the old folder because they were built for another version.

Not on 1.21.11 yet (shown as "Coming soon" in the menu): Motion Blur, Item Physics,
Glint Colorizer, Chunk Borders, Hitboxes, the custom skin/cape in game, and the Block Overlay fill.

If the client fails to build, `build-appimage.sh` still builds the launcher, prints
**FAILED** in red and saves the compiler errors to `build/client-errors.txt` (also copied to
your clipboard); send that file to get it fixed.

The Cobra main menu has a **VANILLA MENU** button (top right): one click gives you Minecraft's
normal title screen; Cobra's comes back after you've played a world.

## Windows 11 (.exe)

On a Windows PC with a JDK 21 installed (e.g. Temurin 21), double-click or run `build-windows.bat`.
It builds Cobra Client and the launcher, then `dist\Cobra Launcher\Cobra Launcher.exe` (a portable
app with its own Java; copy the whole folder anywhere). If the WiX Toolset 3 is installed it also
makes an installer `.exe` in `dist\`.

Don't have Windows handy? Push the project to a GitHub repository: the included workflow
(`.github/workflows/build.yml`) builds **both** the Linux AppImage and the Windows app on GitHub's
machines. Open the repo's **Actions** tab → latest run → **Artifacts** to download them.

## What's new in 2.1.0

- **Microsoft sign-in in one click**: the browser opens with the code already filled in; sign in
  and the launcher logs you in by itself. No Azure app, nothing to copy or paste.
- **Super optimization (Vulkan)** in Settings → Game: adds VulkanMod, Not Enough Vulkan,
  EntityCulling, FerriteCore, MoreCulling, Cloth Config and Clumps (from Modrinth) at launch;
  turning it off removes exactly those.
- **Duplicate mods** (same mod id twice) are cleaned up at launch; Fabric API and Cobra Client
  always come from the launcher's own folder.
- **Cobra Server** (65.109.88.105:25577) is always first in the Multiplayer list and can't be
  deleted.
- In game: **Update to the newest** (Cobra Settings) downloads the latest release for the
  launcher; **Disable all particles**; the Right Shift editor animates open; the glint colour is
  written into Minecraft's own glint texture (works now).
- Windows: the normal Windows 10/11 file picker, folders open in Explorer (not a browser),
  ffmpeg is set up on first start, safer graphics settings for older drivers.
- Glass look: **Frosted** (default, soft and calm) or **Liquid** (clear, bends at the edges).
- Discord: your own top/bottom lines, play time on/off, profile name, a "Get Cobra Client" button.

## Cobra icon in the Tab list

Players using Cobra Client get a small Cobra mark before their name in Tab (for everyone who uses
Cobra). It needs the tiny online-list server in `server/` running on your VPS
(65.109.88.105 by default):

```bash
scp server/cobra-online.py server/cobra-online.service root@65.109.88.105:/tmp/
ssh root@65.109.88.105 'cp /tmp/cobra-online.py /opt/ && cp /tmp/cobra-online.service /etc/systemd/system/ \
  && systemctl enable --now cobra-online && (ufw allow 25581/tcp || true)'
```

Clients send only their Minecraft UUID every 30 s. Players can turn it off in Cobra Settings →
"Cobra icon in Tab".

## Minecraft versions

Every Minecraft release can be played. Cobra Client is built for the versions in
`client/versions.txt` (1.21.11, 1.21.10, 1.21.9): `build-appimage.sh` / `build-windows.bat`
build one client jar per version (`./gradlew build -Pmc=<version>` in `client/`, which looks up
Yarn, Fabric Loader and Fabric API itself) and the launcher bundles every one that built. Versions
whose build fails, and every other release, show under "Without Cobra Client" (Fabric with your
mods, or vanilla before 1.14). Small per-version differences live in `client/versions/<x>/java`.

## Custom capes and cosmetics (only Cobra players see them)

A cape imported in Accessories is uploaded to the online-list server (`server/cobra-online.py`,
`POST /cape`) and other Cobra players download it from there; everyone else sees your normal cape.
Capes are kept in `/var/lib/cobra-online` (the service's state directory).

### Cosmetics

Right Shift → Cosmetics: cat ears, wings (angel, red, black, gold, blue, purple, pink, green), halo
(angel or red), cat tail, katana on your back, big feet and boxing gloves. What you wear is sent to
the online list in `server/` together with your UUID, so other Cobra players see it; everyone else
sees a normal player. Update the server when you update Cobra:

```bash
scp server/cobra-online.py root@65.109.88.105:/opt/ && ssh root@65.109.88.105 systemctl restart cobra-online
```

## Updates for everyone

Launchers built on GitHub know their repo and build number. While running they check the repo's
latest **Release** and download a newer launcher in the background; the next time they're opened
they run the new one (Linux and Windows alike, Cobra Client included).

To publish: push your code, then in the launcher (signed in as **Swipecz**) Settings → Developer →
**Publish update**. That runs the GitHub build with publish = true (needs the GitHub CLI `gh`
logged in on that PC) and creates the release. The repo must be **public** so other people's
launchers can download releases: `gh repo edit --visibility public --accept-visibility-change-consequences`.

## Screen Recorder

In game, turn on **Screen Recorder** (Utility): F9 start, F10 pause/resume, F12 stop (change them
in its options), 1080p or window size, 30 / 60 / 120 fps, quality. Frames are read back from the GPU
without making the game wait and encoded by ffmpeg (set up automatically). Videos land in the
launcher's **Recordings** page.

## Where Cobra Client lives

Cobra Client and Fabric API aren't in the profile's `mods` folder any more: the launcher keeps them in
`~/.local/share/CobraLauncher/client/1.21.11/` (Windows: `%APPDATA%\.cobralauncher\client\1.21.11\`)
and loads them with Fabric's `-Dfabric.addMods`. So they're always loaded, can't be dragged out
of the mods folder, and an old copy can't block the new one. They still show in the Mods page as
**Built in** / **Required**.

## Discord Rich Presence (setup once)

1. <https://discord.com/developers/applications> → **New Application** → name it **Cobra Client**
   (that's the "Playing Cobra Client" text).
2. **Rich Presence → Art Assets → Add Image(s)**: upload the five pictures in `packaging/discord/`
   and keep their names exactly: `logo`, `launcher`, `menu`, `singleplayer`, `server`. Save.
3. **General Information** → copy the **Application ID** into Settings → Discord → Application ID
   (or build it in as `DiscordPresence.DEFAULT_APP_ID` so everyone has it).
4. In Discord: User Settings → Activity Privacy → "Share your detected activities" on.

## Discord Rich Presence

Settings → **Discord** shows "Playing Cobra Client" on your Discord profile: "In the launcher"
while you're in the launcher, then "Playing Minecraft 1.21.11" with "In the menus",
"Singleplayer" or "On mc.eclypse.net" (switch **Show server** off to hide the address), plus
the time played. It talks to Discord directly over its local socket, so it works with the
normal, Flatpak and Snap Discord.

Discord needs an application for this (its name is what appears after "Playing"). One-time,
about a minute:

1. Go to <https://discord.com/developers/applications> → **New Application** → name it
   **Cobra Client**.
2. **Rich Presence → Art Assets** → upload the Cobra logo
   (`launcher/src/main/resources/img/icon_512.png`) with the name
   **logo**.
3. **General Information** → copy the **Application ID** and paste it into Settings → Discord →
   **Application ID**.

Discord has to be running on the same PC; the launcher reconnects by itself if you start Discord
later.

## 6. Troubleshooting

- **403 at the end of sign-in** — Mojang hasn't approved your Azure app id yet (step 2.4).
- **"Enable public client flows"** — Azure → Authentication → *Allow public client flows: Yes*.
- **Wayland** — the game runs through XWayland; that's normal. The launcher window is
  frameless, drag it by the top bar.
- **`jlink` fails** — install `java-21-openjdk-jmods`.
- **Game log** — `~/.local/share/CobraLauncher/logs/<version>-latest.log` (access tokens are
  stripped from it).

## 7. Note on versions

Everything targets **1.21.11**, the version Eclypse runs, so you can join it directly.
