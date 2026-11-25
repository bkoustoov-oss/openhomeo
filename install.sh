#!/usr/bin/env sh
# ==============================================================================
# OpenHomeo Workstation - Universal macOS & Distro-Independent Linux Installer
# Usage: curl -fsSL https://raw.githubusercontent.com/bkoustoov-oss/openhomeo/master/install.sh | sh
# ==============================================================================
set -e

REPO_BASE="https://github.com/bkoustoov-oss/openhomeo"
MANIFEST_URL="https://raw.githubusercontent.com/bkoustoov-oss/openhomeo/master/ota_manifest.json"
VERSION_TAG="v1.3.0"
OS_NAME=$(uname -s)

echo "================================================================================"
echo "  OpenHomeo Classical Workstation - Automated macOS / Linux Installer           "
echo "================================================================================"

# Helper to download via curl or wget cleanly
download_file() {
    url="$1"
    dest="$2"
    if command -v curl >/dev/null 2>&1; then
        curl -fL --progress-bar "$url" -o "$dest"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$dest" "$url"
    else
        echo "[!] Error: neither curl nor wget is installed." >&2
        exit 1
    fi
}

# Detect latest tag from manifest when possible
if command -v curl >/dev/null 2>&1; then
    MANIFEST_JSON=$(curl -fsSL "$MANIFEST_URL" 2>/dev/null || true)
    PARSED_SEMVER=$(printf '%s' "$MANIFEST_JSON" | tr ',' '\n' | awk -F'"' '/"semver"/ {print $4; exit}')
    if [ -n "$PARSED_SEMVER" ]; then
        VERSION_TAG="v$PARSED_SEMVER"
    fi
fi

if [ "$OS_NAME" = "Darwin" ]; then
    ARCH=$(uname -m)
    DMG_URL="$REPO_BASE/releases/download/$VERSION_TAG/OpenHomeo-$VERSION_TAG-macOS-$ARCH.dmg"
    TAR_URL="$REPO_BASE/releases/download/$VERSION_TAG/OpenHomeo-$VERSION_TAG-macOS.tar.gz"
    TMP_DMG="/tmp/OpenHomeo-$VERSION_TAG.dmg"

    echo "[*] Detected macOS ($ARCH). Checking native .dmg release..."
    if download_file "$DMG_URL" "$TMP_DMG" 2>/dev/null; then
        echo "[*] Mounting $TMP_DMG and installing OpenHomeo.app into /Applications..."
        MNT_DIR=$(hdiutil attach "$TMP_DMG" -nobrowse | awk '/\/Volumes\// {for(i=3;i<=NF;i++) printf $i (i<NF?" ":""); exit}')
        if [ -d "$MNT_DIR/OpenHomeo.app" ]; then
            rm -rf /Applications/OpenHomeo.app
            cp -R "$MNT_DIR/OpenHomeo.app" /Applications/OpenHomeo.app
            hdiutil detach "$MNT_DIR" -quiet || true
            rm -f "$TMP_DMG"
            echo "[+] OpenHomeo.app installed to /Applications/OpenHomeo.app!"
            open /Applications/OpenHomeo.app
            exit 0
        fi
        hdiutil detach "$MNT_DIR" -quiet || true
        rm -f "$TMP_DMG"
    fi

    INSTALL_DIR="$HOME/.local/share/openhomeo"
    TMP_TAR="/tmp/OpenHomeo-$VERSION_TAG-macOS.tar.gz"
    echo "[*] Installing universal macOS portable archive to $INSTALL_DIR..."
    mkdir -p "$INSTALL_DIR"
    download_file "$TAR_URL" "$TMP_TAR"
    tar -xzf "$TMP_TAR" -C "$INSTALL_DIR" --strip-components=1
    rm -f "$TMP_TAR"
    chmod +x "$INSTALL_DIR"/*.sh 2>/dev/null || true
    exec "$INSTALL_DIR/openhomeo.sh"
fi

# Distro-independent Linux installation
INSTALL_DIR="${OPENHOMEO_HOME:-$HOME/.local/share/openhomeo}"
BIN_DIR="$HOME/.local/bin"
DESKTOP_DIR="$HOME/.local/share/applications"
TAR_URL="$REPO_BASE/releases/download/$VERSION_TAG/OpenHomeo-$VERSION_TAG-Linux-x86_64.tar.gz"
TMP_TAR="/tmp/OpenHomeo-$VERSION_TAG-Linux.tar.gz"

echo "[1/3] Downloading OpenHomeo $VERSION_TAG generic Linux archive..."
download_file "$TAR_URL" "$TMP_TAR"

echo "[2/3] Extracting into $INSTALL_DIR (user clinic database ~/.openhomeo preserved)..."
mkdir -p "$INSTALL_DIR" "$BIN_DIR" "$DESKTOP_DIR"
tar -xzf "$TMP_TAR" -C "$INSTALL_DIR" --strip-components=1
rm -f "$TMP_TAR"
chmod +x "$INSTALL_DIR"/*.sh 2>/dev/null || true

cat > "$BIN_DIR/openhomeo" <<EOF
#!/usr/bin/env sh
exec "$INSTALL_DIR/openhomeo.sh" "\$@"
EOF
chmod +x "$BIN_DIR/openhomeo"

cat > "$DESKTOP_DIR/openhomeo.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=OpenHomeo Workstation
Comment=Classical Homeopathy Repertory & Clinic Workstation
Exec="$INSTALL_DIR/openhomeo.sh"
Icon=$INSTALL_DIR/openhomeo.png
Terminal=false
Categories=MedicalSoftware;Office;Science;
EOF

echo "[3/3] Creating uninstaller: $INSTALL_DIR/uninstall-linux.sh ..."
if [ ! -f "$INSTALL_DIR/uninstall-linux.sh" ]; then
cat > "$INSTALL_DIR/uninstall-linux.sh" <<'EOF'
#!/usr/bin/env sh
set -e
APP_DIR=$(cd "$(dirname "$0")" && pwd)
rm -f "$HOME/.local/bin/openhomeo" "$HOME/.local/share/applications/openhomeo.desktop"
printf "Keep your clinic database (~/.openhomeo)? [Y/n]: "
read ans || ans="y"
case "$ans" in
  n|N) rm -rf "$HOME/.openhomeo" ;;
esac
rm -rf "$APP_DIR"
echo "OpenHomeo Workstation uninstalled."
EOF
chmod +x "$INSTALL_DIR/uninstall-linux.sh"
fi

echo "================================================================================"
echo "  SUCCESS! OpenHomeo $VERSION_TAG installed silently (no terminal required)!"
echo "  Run 'openhomeo' or launch 'OpenHomeo Workstation' from your app menu."
echo "================================================================================"
nohup "$INSTALL_DIR/openhomeo.sh" >/dev/null 2>&1 &
