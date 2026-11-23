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

# 3. Clone / Update Repository
if [ -d "$INSTALL_DIR/.git" ]; then
    echo -e "${CYAN}[*] Updating existing OpenHomeo repository...${NC}"
    git -C "$INSTALL_DIR" pull
else
    echo -e "${CYAN}[*] Cloning OpenHomeo from GitHub...${NC}"
    git clone https://github.com/bkoustoov-oss/openhomeo.git "$INSTALL_DIR"
fi

# 4. Build Native Core and Java Workstation
cd "$INSTALL_DIR"
echo -e "${CYAN}[*] Compiling native Rust SIMD core...${NC}"
cd "$INSTALL_DIR/native-core"
cargo build --release

echo -e "${CYAN}[*] Compiling OpenHomeo workstation with Maven...${NC}"
cd "$INSTALL_DIR"
mvn clean package -DskipTests

# 5. Create CLI Launcher
BIN_DIR="$HOME/.local/bin"
mkdir -p "$BIN_DIR"
LAUNCHER="$BIN_DIR/openhomeo"

cat << EOF > "$LAUNCHER"
#!/usr/bin/env bash
cd "$INSTALL_DIR"
exec mvn javafx:run -pl openhomeo-gui
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
