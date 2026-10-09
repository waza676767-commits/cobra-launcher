#!/usr/bin/env bash
# Builds LifeLauncher-x86_64.AppImage:
#   1. client/   -> life-client-1.21.11 jar  (the Life Client mod, Fabric 1.21.11)
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
APPDIR="$OUT/LifeLauncher.AppDir"
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
    rm -f client/build/libs/life-client-*.jar
    BUILT=""; SKIPPED=""
    if [[ -z "${SKIP_CLIENTS:-}" ]]; then
        chmod +x client/gradlew
        FIRST=1
        # one Life Client per Minecraft version in client/versions.txt
        while read -r MCV; do
            [[ -z "$MCV" || "$MCV" == \#* ]] && continue
            step "Building Life Client for Minecraft $MCV"
            if (cd client && ./gradlew --no-daemon build -Pmc="$MCV" 2>&1 | tee "$LOG.$MCV"; exit "${PIPESTATUS[0]}"); then
                BUILT="$BUILT $MCV"
            else
                rm -f client/build/libs/life-client-"$MCV"-*.jar
                if [[ -n "$FIRST" ]]; then
                    cp "$LOG.$MCV" "$LOG"
                    grep -E "error:|warning: \[|Mixin|What went wrong|> Could not|FAILED" -A3 "$LOG" | head -n 150 > "$ERRORS"
                    printf '\n\033[1;31m==> Life Client for %s failed to build. The errors:\033[0m\n' "$MCV"
                    grep -E "error:" -A2 "$LOG" | head -n 40 || tail -n 40 "$LOG"
                    if command -v wl-copy >/dev/null; then wl-copy < "$LOG" && echo "(the whole log is copied to your clipboard: paste it to get it fixed)"
                    elif command -v xclip >/dev/null; then xclip -selection clipboard < "$LOG" && echo "(the whole log is copied to your clipboard: paste it to get it fixed)"; fi
                    die "Life Client build failed. Send $LOG to get it fixed. No AppImage was packaged."
                fi
                SKIPPED="$SKIPPED $MCV"
                printf '\033[1;33m==> Life Client for %s did not build: that version runs without Life (log: %s)\033[0m\n' "$MCV" "$LOG.$MCV"
            fi
            FIRST=""
        done < client/versions.txt
        # gradle "build" cleans nothing between versions, but make sure only real mod jars are bundled
        rm -f client/build/libs/*-sources.jar client/build/libs/*-dev.jar
    fi
    step "Building the launcher"
    chmod +x gradlew
    ./gradlew --no-daemon :launcher:clean :launcher:jar
    JAR="$ROOT/launcher/build/libs/life-launcher.jar"
fi
[[ -f "$JAR" ]] || die "Launcher jar not found at $JAR"

# ---------------------------------------------------------------- report
LISTED=$(unzip -l "$JAR" 2>/dev/null | grep -oE "bundled/life-client-[0-9.]+\.jar" | sed -E 's#bundled/life-client-(.*)\.jar#\1#' | sort -Vr | tr '\n' ' ' || true)
if [[ -n "$LISTED" ]]; then
    printf '\n\033[1;32m==> Life Client built in for: %s\033[0m\n' "$LISTED"
    [[ -n "${SKIPPED:-}" ]] && printf '\033[1;33m==> Without Life Client (did not build):%s\033[0m\n' "$SKIPPED"
else
    printf '\n\033[1;31m==> Life Client: FAILED to build\033[0m (the launcher still works, the game runs without Life)\n'
    if [[ -s "${ERRORS:-/nonexistent}" ]]; then
        printf '    Send this file so it can be fixed:  %s\n' "$ERRORS"
        command -v wl-copy >/dev/null && wl-copy < "$ERRORS" && echo "    (it's copied to your clipboard: just paste it)"
    fi
fi

# ---------------------------------------------------------------- AppDir
step "Assembling AppDir"
rm -rf "$APPDIR"
mkdir -p "$APPDIR/usr/lib" "$APPDIR/usr/share/applications" "$APPDIR/usr/share/icons/hicolor/256x256/apps"
cp "$JAR" "$APPDIR/usr/lib/life-launcher.jar"

step "Creating trimmed Java runtime (jlink)"
"$JAVA_HOME/bin/jlink" \
    --add-modules java.base,java.desktop,java.net.http,java.logging,java.management,jdk.management,java.naming,jdk.crypto.ec,jdk.unsupported \
    --strip-debug --no-header-files --no-man-pages --compress=zip-6 \
    --output "$APPDIR/usr/runtime"

ICON="$ROOT/launcher/src/main/resources/img/icon_256.png"
cp "$ICON" "$APPDIR/life-launcher.png"
cp "$ICON" "$APPDIR/usr/share/icons/hicolor/256x256/apps/life-launcher.png"
ln -sf life-launcher.png "$APPDIR/.DirIcon"

cat > "$APPDIR/life-launcher.desktop" << 'DESKTOP'
[Desktop Entry]
Type=Application
Name=Life Launcher
Comment=Minecraft launcher for Life Client
Exec=life-launcher
Icon=life-launcher
Categories=Game;
StartupWMClass=life-launcher
Terminal=false
DESKTOP
cp "$APPDIR/life-launcher.desktop" "$APPDIR/usr/share/applications/"

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
    -jar "$HERE/usr/lib/life-launcher.jar" "$@"
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
TARGET="$ROOT/LifeLauncher-x86_64.AppImage"
rm -f "$TARGET"
# APPIMAGE_EXTRACT_AND_RUN avoids needing FUSE for the tool itself
ARCH=x86_64 APPIMAGE_EXTRACT_AND_RUN=1 "$TOOL" --no-appstream "$APPDIR" "$TARGET"
chmod +x "$TARGET"

# install as an app: ~/Applications + a menu entry you can search for ("Life")
if [ "${1:-}" != "--no-install" ]; then
    step "Installing to ~/Applications"
    mkdir -p "$HOME/Applications"
    rm -f "$HOME/Applications/KitLauncher.AppImage"        # remove the Kit test build if it's there
    # the launcher's builds from before it was called Life, and their menu entry
    rm -f "$HOME/Applications/CobraLauncher.AppImage" "$HOME/Desktop/CobraLauncher.AppImage" \
          "${XDG_DATA_HOME:-$HOME/.local/share}/applications/cobra-launcher.desktop"
    cp -f "$TARGET" "$HOME/Applications/LifeLauncher.AppImage"
    chmod +x "$HOME/Applications/LifeLauncher.AppImage"
    DATA="${XDG_DATA_HOME:-$HOME/.local/share}"
    for s in 32 48 64 128 256 512; do
        mkdir -p "$DATA/icons/hicolor/${s}x${s}/apps"
        cp -f "$ROOT/launcher/src/main/resources/img/icon_$s.png" "$DATA/icons/hicolor/${s}x${s}/apps/life-launcher.png" 2>/dev/null || true
    done
    mkdir -p "$DATA/applications"
    cat > "$DATA/applications/life-launcher.desktop" << EOF
[Desktop Entry]
Type=Application
Name=Life Launcher
GenericName=Minecraft Launcher
Comment=Minecraft launcher for Life Client
Exec="$HOME/Applications/LifeLauncher.AppImage" %U
TryExec=$HOME/Applications/LifeLauncher.AppImage
Icon=life-launcher
Categories=Game;
Keywords=minecraft;life;launcher;pvp;client;
StartupWMClass=life-launcher
Terminal=false
EOF
    chmod +x "$DATA/applications/life-launcher.desktop"
    command -v update-desktop-database >/dev/null && update-desktop-database "$DATA/applications" 2>/dev/null || true
    command -v kbuildsycoca6 >/dev/null && kbuildsycoca6 >/dev/null 2>&1 || true
fi

step "Done"
echo "  $TARGET ($(du -h "$TARGET" | cut -f1))"
if [ "${1:-}" != "--no-install" ]; then
    echo "  Installed: search \"Life\" in your app menu, or run ~/Applications/LifeLauncher.AppImage"
else
    echo "  Run it with: ./LifeLauncher-x86_64.AppImage"
fi
