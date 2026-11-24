#!/usr/bin/env bash
# ==============================================================================
# OpenHomeo Workstation - macOS & Linux One-Line Automated Installer
# Usage: curl -fsSL https://raw.githubusercontent.com/bkoustoov-oss/openhomeo/master/install.sh | bash
# ==============================================================================

set -e

# Terminal Colors
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${CYAN}"
cat << "EOF"
  ██████╗ ██████╗ ███████╗███╗   ██╗██╗  ██╗ ██████╗ ███╗   ███╗███████╗ ██████╗ 
 ██╔═══██╗██╔══██╗██╔════╝████╗  ██║██║  ██║██╔═══██╗████╗ ████║██╔════╝██╔═══██╗
 ██║   ██║██████╔╝█████╗  ██╔██╗ ██║███████║██║   ██║██╔████╔██║█████╗  ██║   ██║
 ██║   ██║██╔═══╝ ██╔══╝  ██║╚██╗██║██╔══██║██║   ██║██║╚██╔╝██║██╔══╝  ██║   ██║
 ╚██████╔╝██║     ███████╗██║ ╚████║██║  ██║╚██████╔╝██║ ╚═╝ ██║███████╗╚██████╔╝
  ╚═════╝ ╚═╝     ╚══════╝╚═╝  ╚═══╝╚═╝  ╚═╝ ╚═════╝ ╚═╝     ╚═╝╚══════╝ ╚═════╝ 
        Classical Homeopathy Workstation • 100% Free Open-Source Software
EOF
echo -e "${NC}"

echo -e "${GREEN}[*] Starting automated deployment of OpenHomeo Workstation...${NC}"

OS="$(uname -s)"
ARCH="$(uname -m)"
echo -e "[*] Detected Operating System: ${OS} (${ARCH})"

# 1. Dependency Resolution
if [ "$OS" = "Darwin" ]; then
    echo -e "[*] macOS detected. Checking Homebrew..."
    if ! command -v brew &>/dev/null; then
        echo -e "${YELLOW}[!] Homebrew not found. Please install Homebrew from https://brew.sh/${NC}"
    else
        if ! command -v java &>/dev/null || [ "$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)" -lt 21 ]; then
            echo -e "${CYAN}[*] Installing OpenJDK 21 via Homebrew...${NC}"
            brew install openjdk@21
            sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk || true
        fi
        command -v mvn &>/dev/null || brew install maven
        command -v cargo &>/dev/null || brew install rust
    fi
elif [ "$OS" = "Linux" ]; then
    echo -e "[*] Linux detected. Checking package manager..."
    if command -v apt-get &>/dev/null; then
        sudo apt-get update -y
        sudo apt-get install -y openjdk-21-jdk maven cargo git build-essential
    elif command -v dnf &>/dev/null; then
        sudo dnf install -y java-21-openjdk-devel maven cargo git gcc
    elif command -v pacman &>/dev/null; then
        sudo pacman -Sy --noconfirm jdk21-openjdk maven rust git base-devel
    fi
fi

# 2. Target Directory
INSTALL_DIR="$HOME/.local/share/openhomeo"
mkdir -p "$INSTALL_DIR"
echo -e "[*] Installation path: ${INSTALL_DIR}"

# 3. Check for pre-compiled release package
DOWNLOADED_RELEASE=false
MAC_RELEASE_URL="https://github.com/bkoustoov-oss/openhomeo/releases/download/v1.1.0/OpenHomeo-v1.1.0-macOS.tar.gz"

if [ "$OS" = "Darwin" ]; then
    echo -e "${CYAN}[*] Checking for OpenHomeo v1.1.0 macOS release package...${NC}"
    if curl -fsSL --connect-timeout 10 "$MAC_RELEASE_URL" -o /tmp/openhomeo_mac.tar.gz 2>/dev/null; then
        echo -e "${GREEN}[*] Extracting pre-compiled macOS package into ${INSTALL_DIR}...${NC}"
        tar -xzf /tmp/openhomeo_mac.tar.gz -C "$INSTALL_DIR" --strip-components=1
        DOWNLOADED_RELEASE=true
        echo -e "${GREEN}[+] macOS package extracted successfully.${NC}"
    fi
fi

if [ "$DOWNLOADED_RELEASE" = false ]; then
    # Clone / Update Repository
    if [ -d "$INSTALL_DIR/.git" ]; then
        echo -e "${CYAN}[*] Updating existing OpenHomeo repository...${NC}"
        git -C "$INSTALL_DIR" pull
    else
        echo -e "${CYAN}[*] Cloning OpenHomeo from GitHub...${NC}"
        git clone https://github.com/bkoustoov-oss/openhomeo.git "$INSTALL_DIR"
    fi

    # 4. Build Native Core and Java Workstation
    cd "$INSTALL_DIR"
    if [ -d "$INSTALL_DIR/native-core" ] && command -v cargo &>/dev/null; then
        echo -e "${CYAN}[*] Compiling native Rust SIMD core...${NC}"
        cd "$INSTALL_DIR/native-core"
        cargo build --release || true
    fi

    echo -e "${CYAN}[*] Compiling OpenHomeo workstation with Maven...${NC}"
    cd "$INSTALL_DIR"
    mvn clean package -DskipTests
fi

# 5. Create CLI Launcher
BIN_DIR="$HOME/.local/bin"
mkdir -p "$BIN_DIR"
LAUNCHER="$BIN_DIR/openhomeo"

cat << 'EOF' > "$LAUNCHER"
#!/usr/bin/env bash
INSTALL_DIR="$HOME/.local/share/openhomeo"
cd "$INSTALL_DIR"
if [ -f "$INSTALL_DIR/run-openhomeo.sh" ]; then
    chmod +x "$INSTALL_DIR/run-openhomeo.sh"
    exec "$INSTALL_DIR/run-openhomeo.sh"
else
    exec mvn javafx:run -pl openhomeo-gui
fi
EOF
chmod +x "$LAUNCHER"

echo -e "${GREEN}
================================================================================
[+] OpenHomeo Workstation successfully installed!
================================================================================
  • Binary Command:   $LAUNCHER (Make sure ~/.local/bin is in your PATH)
  • Bedside LAN App:  http://localhost:8090 (PIN: doctor123 / admin123)
  • Clinic Database:  ~/.openhomeo/user_clinic.db (AES-256 Encrypted)

To run OpenHomeo now:
  openhomeo
  OR
  cd '$INSTALL_DIR' && mvn javafx:run -pl openhomeo-gui
${NC}"
