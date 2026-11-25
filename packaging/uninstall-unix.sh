#!/usr/bin/env sh
# ==============================================================================
# OpenHomeo Workstation - Safe Linux & macOS Uninstaller
# Preserves user_clinic.db patient records by default unless explicitly wiped.
# ==============================================================================
set -e

APP_DIR=$(cd "$(dirname "$0")" && pwd)
OS_NAME=$(uname -s)

echo "Uninstalling OpenHomeo Workstation from $APP_DIR ..."

if [ "$OS_NAME" = "Darwin" ]; then
    rm -rf /Applications/OpenHomeo.app 2>/dev/null || true
    rm -f "$HOME/Desktop/OpenHomeo Workstation.command" 2>/dev/null || true
else
    rm -f "$HOME/.local/bin/openhomeo" 2>/dev/null || true
    rm -f "$HOME/.local/share/applications/openhomeo.desktop" 2>/dev/null || true
fi

printf "Keep your clinic database & settings (~/.openhomeo/user_clinic.db)? [Y/n]: "
read ans || ans="y"
case "$ans" in
    n|N)
        rm -rf "$HOME/.openhomeo"
        echo "[*] Removed user clinic data directory (~/.openhomeo)."
        ;;
    *)
        echo "[*] Preserved clinic database in ~/.openhomeo."
        ;;
esac

if [ "$APP_DIR" != "/" ] && [ -n "$APP_DIR" ]; then
    cd "$HOME"
    rm -rf "$APP_DIR"
fi

echo "[+] OpenHomeo Workstation uninstalled cleanly."
