#!/usr/bin/env bash
# Builds CobraLauncher-x86_64.AppImage:
#   1. client/   -> cobra-client-1.21.11 jar  (the Cobra Client mod, Fabric 1.21.11)
#   2. launcher fat jar with the client jar bundled inside
#   3. a trimmed Java 21 runtime (jlink) + AppDir + appimagetool
#
# Needs a full JDK 21 with jmods. On Nobara/Fedora:
#   sudo dnf install java-21-openjdk-devel java-21-openjdk-jmods
# Env: SKIP_CLIENTS=1 builds the launcher only (no in-game client).
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")"
ROOT="$PWD"
OUT="$ROOT/build/appimage"
APPDIR="$OUT/CobraLauncher.AppDir"
TOOLS="$ROOT/build/tools"

step() { printf '\n\033[1;37m==> %s\033[0m\n' "$*"; }
die() { printf '\033[1;31merror:\033[0m %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------- JDK
if [[ -z "${JAVA_HOME:-}" ]]; then
    command -v javac >/dev/null || die "javac not found. Install a JDK 21 (see top of this script)."
    JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
fi
export JAVA_HOME
"$JAVA_HOME/bin/java" -version 2>&1 | grep -q 'version "2[1-9]' || die "JAVA_HOME ($JAVA_HOME) must be JDK 21 or newer."
[[ -d "$JAVA_HOME/jmods" ]] || die "No jmods in $JAVA_HOME. On Fedora/Nobara: sudo dnf install java-21-openjdk-jmods"

# ---------------------------------------------------------------- updater info
# Local builds: take the GitHub repo from git (so this build also finds updates). GitHub's own
# build writes the build number itself (KEEP_UPDATE_PROPERTIES).
if [[ -z "${KEEP_UPDATE_PROPERTIES:-}" ]]; then
    REPO_URL=$(git config --get remote.origin.url 2>/dev/null || true)
    REPO_NAME=$(echo "$REPO_URL" | sed -nE 's#.*github\.com[:/]([^/]+/[^/.]+)(\.git)?$#\1#p')
    printf 'repo=%s\nbuild=999999\n' "$REPO_NAME" > launcher/src/main/resources/update.properties   # your own build: never swapped for an older GitHub release
fi

# ---------------------------------------------------------------- client + launcher
CLIENT_OK=""
if [[ -n "${LAUNCHER_JAR:-}" ]]; then
    JAR="$LAUNCHER_JAR"   # prebuilt jar (used for testing the packaging step)
else
    mkdir -p "$ROOT/build"
    ERRORS="$ROOT/build/client-errors.txt"
    LOG="$ROOT/build/client-build.log"
    : > "$ERRORS"
    rm -f client/build/libs/cobra-client-1.21.11-*.jar
    if [[ -z "${SKIP_CLIENTS:-}" ]]; then
        step "Building Cobra Client (Minecraft 1.21.11, Fabric)"
        # newest yarn / loader / Fabric API for 1.21.11, looked up live so they're always valid
        PROPS=$(python3 - << 'PYV'
import json, urllib.request, urllib.parse
v = "1.21.11"
def get(u):
    req = urllib.request.Request(u, headers={"User-Agent": "cobra-launcher-build"})
    return json.load(urllib.request.urlopen(req, timeout=30))
try:
    yarn = get("https://meta.fabricmc.net/v2/versions/yarn/" + v)[0]["version"]
    loader = next(l["version"] for l in get("https://meta.fabricmc.net/v2/versions/loader") if l.get("stable"))
    q = urllib.parse.urlencode({"game_versions": json.dumps([v]), "loaders": json.dumps(["fabric"])})
    api = get("https://api.modrinth.com/v2/project/fabric-api/version?" + q)[0]["version_number"]
    print(f"-Pyarn_mappings={yarn} -Ploader_version={loader} -Pfabric_version={api}")
except Exception:
    print("")
PYV
)
        if (cd client && chmod +x gradlew && ./gradlew --no-daemon build $PROPS 2>&1 | tee "$LOG"; exit "${PIPESTATUS[0]}"); then
            CLIENT_OK=1
        else
            grep -E "error:|warning: \[|Mixin|What went wrong|> Could not|FAILED" -A3 "$LOG" | head -n 150 > "$ERRORS"
            rm -f client/build/libs/cobra-client-1.21.11-*.jar
            printf '\n\033[1;31m==> Cobra Client failed to build. The errors:\033[0m\n'
            grep -E "error:" -A2 "$LOG" | head -n 40 || tail -n 40 "$LOG"
            if command -v wl-copy >/dev/null; then wl-copy < "$LOG" && echo "(the whole log is copied to your clipboard: paste it to get it fixed)"
            elif command -v xclip >/dev/null; then xclip -selection clipboard < "$LOG" && echo "(the whole log is copied to your clipboard: paste it to get it fixed)"; fi
            die "Cobra Client build failed. Send $LOG to get it fixed. No AppImage was packaged."
        fi
    fi
    step "Building the launcher"
    chmod +x gradlew
    ./gradlew --no-daemon :launcher:clean :launcher:jar
    JAR="$ROOT/launcher/build/libs/cobra-launcher.jar"
fi
[[ -f "$JAR" ]] || die "Launcher jar not found at $JAR"

# ---------------------------------------------------------------- report
if unzip -l "$JAR" 2>/dev/null | grep -q "bundled/cobra-client-1.21.11.jar"; then
    printf '\n\033[1;32m==> Cobra Client for 1.21.11: OK\033[0m (bundled in the launcher)\n'
else
    printf '\n\033[1;31m==> Cobra Client for 1.21.11: FAILED to build\033[0m (the launcher still works, the game runs without Cobra)\n'
    if [[ -s "${ERRORS:-/nonexistent}" ]]; then
        printf '    Send this file so it can be fixed:  %s\n' "$ERRORS"
        command -v wl-copy >/dev/null && wl-copy < "$ERRORS" && echo "    (it's copied to your clipboard: just paste it)"
    fi
fi

# ---------------------------------------------------------------- AppDir
step "Assembling AppDir"
rm -rf "$APPDIR"
mkdir -p "$APPDIR/usr/lib" "$APPDIR/usr/share/applications" "$APPDIR/usr/share/icons/hicolor/256x256/apps"
cp "$JAR" "$APPDIR/usr/lib/cobra-launcher.jar"

step "Creating trimmed Java runtime (jlink)"
"$JAVA_HOME/bin/jlink" \
    --add-modules java.base,java.desktop,java.net.http,java.logging,java.management,jdk.management,java.naming,jdk.crypto.ec,jdk.unsupported \
    --strip-debug --no-header-files --no-man-pages --compress=zip-6 \
    --output "$APPDIR/usr/runtime"

ICON="$ROOT/launcher/src/main/resources/img/icon_256.png"
cp "$ICON" "$APPDIR/cobra-launcher.png"
cp "$ICON" "$APPDIR/usr/share/icons/hicolor/256x256/apps/cobra-launcher.png"
ln -sf cobra-launcher.png "$APPDIR/.DirIcon"

cat > "$APPDIR/cobra-launcher.desktop" << 'DESKTOP'
[Desktop Entry]
Type=Application
Name=Cobra Launcher
Comment=Minecraft launcher for Cobra Client
Exec=cobra-launcher
Icon=cobra-launcher
Categories=Game;
StartupWMClass=cobra-launcher
Terminal=false
DESKTOP
cp "$APPDIR/cobra-launcher.desktop" "$APPDIR/usr/share/applications/"

cat > "$APPDIR/AppRun" << 'APPRUN'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"

# Java draws the pointer through X11 (XWayland on Plasma). Tell it your cursor theme and size,
# or it falls back to the old black X11 arrow / hand at the wrong size.
if [ -z "$XCURSOR_THEME" ]; then
    T=$(kreadconfig6 --file kcminputrc --group Mouse --key cursorTheme 2>/dev/null)
    [ -z "$T" ] && T=$(kreadconfig5 --file kcminputrc --group Mouse --key cursorTheme 2>/dev/null)
    [ -z "$T" ] && T=$(gsettings get org.gnome.desktop.interface cursor-theme 2>/dev/null | tr -d "'")
    [ -z "$T" ] && [ -d /usr/share/icons/breeze_cursors ] && T=breeze_cursors
    [ -n "$T" ] && export XCURSOR_THEME="$T"
fi
if [ -z "$XCURSOR_SIZE" ]; then
    S=$(kreadconfig6 --file kcminputrc --group Mouse --key cursorSize 2>/dev/null)
    [ -z "$S" ] && S=$(kreadconfig5 --file kcminputrc --group Mouse --key cursorSize 2>/dev/null)
    [ -z "$S" ] && S=$(gsettings get org.gnome.desktop.interface cursor-size 2>/dev/null)
    export XCURSOR_SIZE="${S:-24}"
fi

exec "$HERE/usr/runtime/bin/java" \
    -Xms96m -Xmx640m -XX:+UseG1GC -XX:MaxGCPauseMillis=8 -Xshare:auto \
    -Dawt.useSystemAAFontSettings=on -Dswing.aatext=true \
    -Dsun.java2d.xrender=false -Dswing.bufferPerWindow=false \
    --add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED \
    -jar "$HERE/usr/lib/cobra-launcher.jar" "$@"
APPRUN
chmod +x "$APPDIR/AppRun"

# ---------------------------------------------------------------- appimagetool
step "Packing AppImage"
mkdir -p "$TOOLS"
TOOL="$TOOLS/appimagetool-x86_64.AppImage"
if [[ ! -x "$TOOL" ]]; then
    curl -fL --retry 3 -o "$TOOL" "https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage"
    chmod +x "$TOOL"
fi
TARGET="$ROOT/CobraLauncher-x86_64.AppImage"
rm -f "$TARGET"
# APPIMAGE_EXTRACT_AND_RUN avoids needing FUSE for the tool itself
ARCH=x86_64 APPIMAGE_EXTRACT_AND_RUN=1 "$TOOL" --no-appstream "$APPDIR" "$TARGET"
chmod +x "$TARGET"

# install as an app: ~/Applications + a menu entry you can search for ("Cobra")
if [ "${1:-}" != "--no-install" ]; then
    step "Installing to ~/Applications"
    mkdir -p "$HOME/Applications"
    cp -f "$TARGET" "$HOME/Applications/CobraLauncher.AppImage"
    chmod +x "$HOME/Applications/CobraLauncher.AppImage"
    DATA="${XDG_DATA_HOME:-$HOME/.local/share}"
    for s in 32 48 64 128 256 512; do
        mkdir -p "$DATA/icons/hicolor/${s}x${s}/apps"
        cp -f "$ROOT/launcher/src/main/resources/img/icon_$s.png" "$DATA/icons/hicolor/${s}x${s}/apps/cobra-launcher.png" 2>/dev/null || true
    done
    mkdir -p "$DATA/applications"
    cat > "$DATA/applications/cobra-launcher.desktop" << EOF
[Desktop Entry]
Type=Application
Name=Cobra Launcher
GenericName=Minecraft Launcher
Comment=Minecraft launcher for Cobra Client
Exec="$HOME/Applications/CobraLauncher.AppImage" %U
TryExec=$HOME/Applications/CobraLauncher.AppImage
Icon=cobra-launcher
Categories=Game;
Keywords=minecraft;cobra;launcher;pvp;client;
StartupWMClass=cobra-launcher
Terminal=false
EOF
    chmod +x "$DATA/applications/cobra-launcher.desktop"
    command -v update-desktop-database >/dev/null && update-desktop-database "$DATA/applications" 2>/dev/null || true
    command -v kbuildsycoca6 >/dev/null && kbuildsycoca6 >/dev/null 2>&1 || true
fi

step "Done"
echo "  $TARGET ($(du -h "$TARGET" | cut -f1))"
if [ "${1:-}" != "--no-install" ]; then
    echo "  Installed: search \"Cobra\" in your app menu, or run ~/Applications/CobraLauncher.AppImage"
else
    echo "  Run it with: ./CobraLauncher-x86_64.AppImage"
fi
