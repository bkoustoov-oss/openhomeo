#!/usr/bin/env sh
# ==============================================================================
# OpenHomeo Workstation - Distribution-Independent POSIX Linux/macOS Launcher
# Works across Ubuntu, Debian, Fedora, Arch, openSUSE, Rocky, macOS, etc.
# ==============================================================================
set -e

APP_DIR=$(cd "$(dirname "$0")" && pwd)
cd "$APP_DIR"

# Apply any staged pre-boot jar replacement cleanly
if [ -f "$APP_DIR/openhomeo-gui-update.jar" ]; then
    mv -f "$APP_DIR/openhomeo-gui-update.jar" "$APP_DIR/openhomeo-gui-1.0.0-SNAPSHOT.jar" 2>/dev/null || true
fi
if [ -f "$APP_DIR/app/openhomeo-gui-update.jar" ]; then
    mv -f "$APP_DIR/app/openhomeo-gui-update.jar" "$APP_DIR/app/openhomeo-gui-1.0.0-SNAPSHOT.jar" 2>/dev/null || true
fi

# Clean legacy split-package jars if lingering from older releases
rm -f "$APP_DIR/lib"/openhomeo-core*.jar "$APP_DIR/app/lib"/openhomeo-core*.jar 2>/dev/null || true

# 1. Prefer bundled self-contained jpackage binary if packaged alongside this script
if [ -x "$APP_DIR/bin/OpenHomeo" ]; then
    exec "$APP_DIR/bin/OpenHomeo" "$@"
fi
if [ -d "$APP_DIR/OpenHomeo.app" ]; then
    exec open "$APP_DIR/OpenHomeo.app"
fi

# 2. Otherwise locate Java 21+ across standard Linux / macOS locations (distro-independent)
find_java21() {
    for candidate in \
        "${JAVA_HOME:-}/bin/java" \
        "$(command -v java 2>/dev/null || true)" \
        /usr/lib/jvm/java-21*/bin/java \
        /usr/lib/jvm/temurin-21*/bin/java \
        /usr/lib/jvm/jdk-21*/bin/java \
        /usr/lib/jvm/msopenjdk-21*/bin/java \
        /opt/java/*/bin/java \
        /opt/homebrew/opt/openjdk@21/bin/java \
        /usr/local/opt/openjdk@21/bin/java \
        "$HOME/.sdkman/candidates/java/21"*/bin/java
    do
        [ -x "$candidate" ] || continue
        ver=$("$candidate" -version 2>&1 | awk -F'"' '/version/ {print $2; exit}')
        maj=$(printf '%s' "$ver" | cut -d'.' -f1)
        if [ "$maj" -ge 21 ] 2>/dev/null; then
            printf '%s' "$candidate"
            return 0
        fi
    done
    return 1
}

JAVA_BIN=$(find_java21 || true)
if [ -z "$JAVA_BIN" ]; then
    echo "[!] Java 21+ LTS was not found and bundled runtime is absent." >&2
    echo "[!] Install OpenJDK 21 LTS (https://adoptium.net) or use the official tarball with bundled runtime." >&2
    exit 1
fi

CP_JAR="$APP_DIR/openhomeo-gui-1.0.0-SNAPSHOT.jar"
CP_LIB="$APP_DIR/lib/*"
if [ -f "$APP_DIR/app/openhomeo-gui-1.0.0-SNAPSHOT.jar" ]; then
    CP_JAR="$APP_DIR/app/openhomeo-gui-1.0.0-SNAPSHOT.jar"
    CP_LIB="$APP_DIR/app/lib/*"
fi

exec "$JAVA_BIN" --enable-native-access=ALL-UNNAMED -cp "$CP_JAR:$CP_LIB" org.openhomeo.gui.Main "$@"
