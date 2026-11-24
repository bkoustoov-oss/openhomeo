# ==============================================================================
# OpenHomeo Cross-Platform Release Packaging Script (v1.2.0 Diamond)
# ==============================================================================

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$rootDir = $PSScriptRoot
$distDir = Join-Path $rootDir "dist"

Write-Host "[*] Cleaning and creating distribution directory: $distDir" -ForegroundColor Cyan
if (Test-Path $distDir) {
    Remove-Item -Path $distDir -Recurse -Force
}
New-Item -ItemType Directory -Path $distDir | Out-Null

# 1. Package Windows Release
Write-Host "`n[*] Building Windows Portable Release (OpenHomeo-v1.2.0-Windows)..." -ForegroundColor Green
$winDir = Join-Path $distDir "OpenHomeo-v1.2.0-Windows"
New-Item -ItemType Directory -Path $winDir | Out-Null
$winLib = Join-Path $winDir "lib"
New-Item -ItemType Directory -Path $winLib | Out-Null

# Copy GUI Jar
Copy-Item (Join-Path $rootDir "openhomeo-gui\target\openhomeo-gui-1.0.0-SNAPSHOT.jar") -Destination $winDir -Force

# Copy Icon
if (Test-Path (Join-Path $rootDir "openhomeo.ico")) {
    Copy-Item (Join-Path $rootDir "openhomeo.ico") -Destination $winDir -Force
}

# Copy runtime dependencies (filtering out test-scoped jars)
$depDir = Join-Path $rootDir "openhomeo-gui\target\dependency"
if (-not (Test-Path $depDir)) {
    Write-Host "[*] Copying runtime dependencies via Maven..." -ForegroundColor Cyan
    mvn dependency:copy-dependencies -pl openhomeo-gui
}
Get-ChildItem -Path (Join-Path $rootDir "openhomeo-gui\target\dependency\*.jar") | Where-Object {
    $_.Name -notmatch "junit" -and $_.Name -notmatch "opentest4j" -and $_.Name -notmatch "apiguardian"
} | ForEach-Object {
    Copy-Item $_.FullName -Destination $winLib -Force
}

# Copy native rust DLL
if (Test-Path (Join-Path $rootDir "native-core\target\release\openhomeo_native.dll")) {
    $nativeDir = Join-Path $winDir "native-core\target\release"
    New-Item -ItemType Directory -Path $nativeDir -Force | Out-Null
    Copy-Item (Join-Path $rootDir "native-core\target\release\openhomeo_native.dll") -Destination $nativeDir -Force
    Copy-Item (Join-Path $rootDir "native-core\target\release\openhomeo_native.dll") -Destination $winDir -Force
}

# Create Windows Batch Launcher
$batContent = @"
@echo off
title OpenHomeo Classical Workstation v1.2.0 Diamond
cd /d "%~dp0"
echo ================================================================================
echo   OpenHomeo Classical Workstation v1.2.0 Diamond (Windows Portable)
echo ================================================================================
echo.
java --enable-native-access=ALL-UNNAMED -cp "openhomeo-gui-1.0.0-SNAPSHOT.jar;lib/*" org.openhomeo.gui.Main
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [!] Application exited with code %ERRORLEVEL%.
    echo [!] Please make sure Java 21+ LTS is installed and present in your system PATH.
    echo [!] Download OpenJDK 21 LTS: https://adoptium.net/temurin/releases/?version=21
    pause
)
"@
$batContent | Out-File -FilePath (Join-Path $winDir "openhomeo.bat") -Encoding ascii

# Create Windows PowerShell Launcher
$psContent = @"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Set-Location `$PSScriptRoot
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "  Launching OpenHomeo Classical Workstation v1.2.0 Diamond (Windows Portable)   " -ForegroundColor Green
Write-Host "================================================================================" -ForegroundColor Cyan
java --enable-native-access=ALL-UNNAMED -cp "openhomeo-gui-1.0.0-SNAPSHOT.jar;lib/*" org.openhomeo.gui.Main
"@
$psContent | Out-File -FilePath (Join-Path $winDir "run.ps1") -Encoding utf8

# Create Shortcut Generator Script with Shell Cache Notification
$shortcutScript = @'
$wsh = New-Object -ComObject WScript.Shell
$desktop = [System.Environment]::GetFolderPath([System.Environment+SpecialFolder]::Desktop)
$shortcutPath = Join-Path $desktop "OpenHomeo Workstation.lnk"
$shortcut = $wsh.CreateShortcut($shortcutPath)
$shortcut.TargetPath = Join-Path $PSScriptRoot "openhomeo.bat"
$shortcut.WorkingDirectory = $PSScriptRoot
$icoPath = Join-Path $PSScriptRoot "openhomeo.ico"
if (Test-Path $icoPath) {
    $shortcut.IconLocation = "$icoPath,0"
}
$shortcut.Description = "OpenHomeo - Classical Homeopathy Professional Workstation (Diamond Edition)"
$shortcut.Save()

# Notify Windows Explorer Shell to refresh icon cache immediately
try {
    Add-Type -TypeDefinition @"
    using System;
    using System.Runtime.InteropServices;
    public class ShellNotifier {
        [DllImport("shell32.dll")]
        public static extern void SHChangeNotify(int wEventId, int uFlags, IntPtr dwItem1, IntPtr dwItem2);
    }
"@
    [ShellNotifier]::SHChangeNotify(0x08000000, 0, [IntPtr]::Zero, [IntPtr]::Zero)
} catch {}

Write-Host "[+] Desktop shortcut with OpenHomeo icon created successfully!" -ForegroundColor Green
'@
$shortcutScript | Out-File -FilePath (Join-Path $winDir "create-desktop-shortcut.ps1") -Encoding utf8

# Create Windows Readme
$winReadme = @"
================================================================================
OpenHomeo Workstation - Version 1.2.0 Diamond Edition (Windows Portable)
Classical Homeopathy Clinical, Repertorization & Dispensary System (100% Free FOSS)
================================================================================

HOW TO RUN:
1. Double-click 'openhomeo.bat' or right-click 'run.ps1' -> Run with PowerShell.
2. Requirements: Java 21 LTS or newer.
   (Free download from: https://adoptium.net/temurin/releases/?version=21)

DEFAULT CLINIC ACCOUNTS:
• Administrator:         admin       / admin123
• Consulting Physician:  doctor      / doctor123
• Pharmacist:            pharmacist  / pharmacist123

NEW IN VERSION 1.2.0 DIAMOND EDITION:
• OpenSynthesis Repertory Scale:
  Architecture with millions of additions, degrees (1-4), classical author citations
  (k1, b1, bog1, a1, c1, boer1, h1, her1, ccrh24), and cross-reference synonym network.
• 1,500-Book Materia Medica Digital Library:
  High-speed SQLite FTS5 + BM25 snippet search across classical treatises
  (Hahnemann, Kent, Boericke, Clarke, Nash, Hering, Boger, Allen, CCRH).
• Families Tool & Taxonomy Classifier:
  APG IV Plant classification, Jan Scholten Periodic Table Series 1-7 & Stages 1-18,
  Animal phyla/classes, Nosodes, and Classical Miasms with distribution analytics.
• WinChip-Alike Patient Management Module:
  Longitudinal consultation follow-up comparison (side-by-side Visit N vs Visit N-1),
  automated Kent's 12 Observations evaluator, and Hering's Law directional cure tracker.
• Upgraded Socratic Clinical Logic AI (Strictly Deterministic, NO LLM):
  Active Socratic questioning on proceedings (thermal contradiction challenges, posology
  repetition hazards, follow-up interventions) and Doctor Supersede with permanent Experience Vault.
• 100% Non-destructive SQLite/AES-256 Storage:
  Your local patient records (user_clinic.db) remain strictly isolated, safe, and untouched.

PROJECT WEBSITE & REPOSITORY:
https://github.com/bkoustoov-oss/openhomeo
================================================================================
"@
$winReadme | Out-File -FilePath (Join-Path $winDir "README.txt") -Encoding utf8

# Compress Windows ZIP
Write-Host "[*] Compressing OpenHomeo-v1.2.0-Windows.zip..." -ForegroundColor Cyan
$winZipPath = Join-Path $distDir "OpenHomeo-v1.2.0-Windows.zip"
Compress-Archive -Path "$winDir\*" -DestinationPath $winZipPath -Force
Write-Host "[+] Windows Portable Package created: $winZipPath" -ForegroundColor Green

# 2. Package macOS Release
Write-Host "`n[*] Building macOS Universal Release (OpenHomeo-v1.2.0-macOS)..." -ForegroundColor Green
$macDir = Join-Path $distDir "OpenHomeo-v1.2.0-macOS"
New-Item -ItemType Directory -Path $macDir | Out-Null
$macLib = Join-Path $macDir "lib"
New-Item -ItemType Directory -Path $macLib | Out-Null

# Copy GUI jar and runtime lib
Copy-Item (Join-Path $rootDir "openhomeo-gui\target\openhomeo-gui-1.0.0-SNAPSHOT.jar") -Destination $macDir -Force
Get-ChildItem -Path (Join-Path $rootDir "openhomeo-gui\target\dependency\*.jar") | Where-Object {
    $_.Name -notmatch "junit" -and $_.Name -notmatch "opentest4j" -and $_.Name -notmatch "apiguardian"
} | ForEach-Object {
    Copy-Item $_.FullName -Destination $macLib -Force
}

# Also copy full source reactor so macOS users can run with 'mvn javafx:run -pl openhomeo-gui'
Copy-Item (Join-Path $rootDir "pom.xml") -Destination $macDir -Force
Copy-Item (Join-Path $rootDir "openhomeo-core") -Destination $macDir -Recurse -Force
Copy-Item (Join-Path $rootDir "openhomeo-gui") -Destination $macDir -Recurse -Force
Copy-Item (Join-Path $rootDir "tools") -Destination $macDir -Recurse -Force
Copy-Item (Join-Path $rootDir "native-core") -Destination $macDir -Recurse -Force
if (Test-Path (Join-Path $rootDir "openhomeo.ico")) {
    Copy-Item (Join-Path $rootDir "openhomeo.ico") -Destination $macDir -Force
}

# Create macOS Bash Launcher (run-openhomeo.sh)
$macRunner = @'
#!/usr/bin/env bash
# ==============================================================================
# OpenHomeo Workstation - macOS Universal Launcher (Intel & Apple Silicon)
# ==============================================================================

cd "$(dirname "$0")"

echo "================================================================================"
echo "  Launching OpenHomeo Classical Workstation v1.2.0 Diamond (macOS)            "
echo "================================================================================"

# Verify Java 21+ is available
if ! command -v java &>/dev/null; then
    echo "[!] Java is not found. Please install OpenJDK 21 LTS via Homebrew:"
    echo "    brew install openjdk@21"
    exit 1
fi

JAVA_MAJOR=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_MAJOR" -lt 21 ]; then
    echo "[!] Java $JAVA_MAJOR detected. OpenHomeo requires Java 21+ LTS."
    exit 1
fi

# If Maven is available, use javafx:run for architecture-perfect native JavaFX bindings
if command -v mvn &>/dev/null; then
    echo "[*] Launching via Maven JavaFX runner..."
    exec mvn javafx:run -pl openhomeo-gui
else
    echo "[*] Launching via standalone Java runner..."
    exec java --enable-native-access=ALL-UNNAMED -cp "openhomeo-gui-1.0.0-SNAPSHOT.jar:lib/*" org.openhomeo.gui.Main
fi
'@
$macRunner | Out-File -FilePath (Join-Path $macDir "run-openhomeo.sh") -Encoding utf8

# Create macOS One-Click Setup Script
$macSetup = @'
#!/usr/bin/env bash
# ==============================================================================
# OpenHomeo Workstation - macOS Automated Setup & Dependency Resolver
# ==============================================================================
set -e

cd "$(dirname "$0")"
chmod +x run-openhomeo.sh

echo "[*] Checking macOS system dependencies..."
if ! command -v java &>/dev/null || [ "$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)" -lt 21 ]; then
    if command -v brew &>/dev/null; then
        echo "[*] Installing OpenJDK 21 LTS via Homebrew..."
        brew install openjdk@21 maven
        sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk || true
    else
        echo "[!] Homebrew not detected. Please install OpenJDK 21 LTS from https://adoptium.net"
    fi
fi

./run-openhomeo.sh
'@
$macSetup | Out-File -FilePath (Join-Path $macDir "install-mac.sh") -Encoding utf8

# Create macOS Readme
$macReadme = @"
================================================================================
OpenHomeo Workstation - Version 1.2.0 Diamond Edition macOS Release
Classical Homeopathy Clinical, Repertorization & Dispensary System (100% Free FOSS)
================================================================================

HOW TO RUN ON macOS (Apple Silicon M1/M2/M3/M4 or Intel):
1. Open Terminal in this folder.
2. Make scripts executable and run:
   chmod +x run-openhomeo.sh install-mac.sh
   ./run-openhomeo.sh
3. If dependencies are needed, run:
   ./install-mac.sh

DEFAULT CLINIC ACCOUNTS:
• Administrator:         admin       / admin123
• Consulting Physician:  doctor      / doctor123
• Pharmacist:            pharmacist  / pharmacist123

PROJECT WEBSITE & REPOSITORY:
https://github.com/bkoustoov-oss/openhomeo
================================================================================
"@
$macReadme | Out-File -FilePath (Join-Path $macDir "README.txt") -Encoding utf8

# Compress macOS tar.gz and zip
Write-Host "[*] Compressing OpenHomeo-v1.2.0-macOS.tar.gz..." -ForegroundColor Cyan
$macTarPath = Join-Path $distDir "OpenHomeo-v1.2.0-macOS.tar.gz"
tar -czf $macTarPath -C $distDir "OpenHomeo-v1.2.0-macOS"
Write-Host "[+] macOS tar.gz package created: $macTarPath" -ForegroundColor Green

Write-Host "[*] Compressing OpenHomeo-v1.2.0-macOS.zip..." -ForegroundColor Cyan
$macZipPath = Join-Path $distDir "OpenHomeo-v1.2.0-macOS.zip"
Compress-Archive -Path "$macDir\*" -DestinationPath $macZipPath -Force
Write-Host "[+] macOS ZIP package created: $macZipPath" -ForegroundColor Green

Write-Host "`n================================================================================" -ForegroundColor Green
Write-Host "  Release artifacts generated successfully in $distDir" -ForegroundColor Green
Write-Host "================================================================================" -ForegroundColor Green
Get-ChildItem -Path $distDir -File | Format-Table Name, Length, LastWriteTime
