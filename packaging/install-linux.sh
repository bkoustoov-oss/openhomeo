#!/usr/bin/env sh
# ==============================================================================
# OpenHomeo Workstation - Generic Linux Installer (Distro-Independent)
# Installs into ~/.local/share/openhomeo without needing root, apt, dnf, or snap.
# ==============================================================================
set -e

SRC_DIR=$(cd "$(dirname "$0")" && pwd)
DEST_DIR="${OPENHOMEO_HOME:-$HOME/.local/share/openhomeo}"
BIN_DIR="$HOME/.local/bin"
DESKTOP_DIR="$HOME/.local/share/applications"

echo "[*] Installing OpenHomeo Workstation to $DEST_DIR ..."
mkdir -p "$DEST_DIR" "$BIN_DIR" "$DESKTOP_DIR"

if [ "$SRC_DIR" != "$DEST_DIR" ]; then
    cp -R "$SRC_DIR"/. "$DEST_DIR"/
fi

chmod +x "$DEST_DIR/openhomeo.sh" "$DEST_DIR/uninstall-linux.sh" 2>/dev/null || true
[ -x "$DEST_DIR/bin/OpenHomeo" ] && chmod +x "$DEST_DIR/bin/OpenHomeo"

cat > "$BIN_DIR/openhomeo" <<EOF
#!/usr/bin/env sh
exec "$DEST_DIR/openhomeo.sh" "\$@"
EOF
chmod +x "$BIN_DIR/openhomeo"

cat > "$DESKTOP_DIR/openhomeo.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=OpenHomeo Workstation
Comment=Classical Homeopathy Repertory & Clinic Workstation
Exec="$DEST_DIR/openhomeo.sh"
Icon=$DEST_DIR/openhomeo.png
Terminal=false
Categories=MedicalSoftware;Office;Science;
EOF

echo "[+] Installed cleanly! Launch 'OpenHomeo Workstation' from your application menu or run 'openhomeo'."
