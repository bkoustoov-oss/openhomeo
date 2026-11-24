# ==============================================================================
# OpenHomeo Workstation - Windows One-Line Universal Automated Installer
# Usage: irm https://raw.githubusercontent.com/bkoustoov-oss/openhomeo/master/install.ps1 | iex
# ==============================================================================

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host @"
  ██████╗ ██████╗ ███████╗███╗   ██╗██╗  ██╗ ██████╗ ███╗   ███╗███████╗ ██████╗ 
 ██╔═══██╗██╔══██╗██╔════╝████╗  ██║██║  ██║██╔═══██╗████╗ ████║██╔════╝██╔═══██╗
 ██║   ██║██████╔╝█████╗  ██╔██╗ ██║███████║██║   ██║██╔████╔██║█████╗  ██║   ██║
 ██║   ██║██╔═══╝ ██╔══╝  ██║╚██╗██║██╔══██║██║   ██║██║╚██╔╝██║██╔══╝  ██║   ██║
 ╚██████╔╝██║     ███████╗██║ ╚████║██║  ██║╚██████╔╝██║ ╚═╝ ██║███████╗╚██████╔╝
  ╚═════╝ ╚═╝     ╚══════╝╚═╝  ╚═══╝╚═╝  ╚═╝ ╚═════╝ ╚═╝     ╚═╝╚══════╝ ╚═════╝ 
        Classical Homeopathy Workstation • 100% Free Open-Source Software
"@ -ForegroundColor Cyan

Write-Host "`n[*] Starting automated deployment of OpenHomeo Workstation on Windows..." -ForegroundColor Green

# 1. System Architecture Check
$arch = if ([System.Environment]::Is64BitOperatingSystem) { "x64" } else { "x86" }
Write-Host "[*] Detected System Architecture: $arch (Windows $([System.Environment]::OSVersion.Version))" -ForegroundColor Gray

# 2. Prerequisites Verification (Java 21 LTS)
$javaInstalled = $false
try {
    $javaVer = java -version 2>&1 | Out-String
    if ($javaVer -match 'version "(\d+)') {
        $majorVer = [int]$matches[1]
        if ($majorVer -ge 21) {
            Write-Host "[+] Java $majorVer LTS detected and verified." -ForegroundColor Green
            $javaInstalled = $true
        } else {
            Write-Host "[-] Java $majorVer detected, but OpenHomeo requires Java 21+ LTS." -ForegroundColor Yellow
        }
    }
} catch {
    Write-Host "[-] Java is not found on PATH." -ForegroundColor Yellow
}

if (-not $javaInstalled) {
    Write-Host "[*] Installing Microsoft OpenJDK 21 LTS via Windows Package Manager (winget)..." -ForegroundColor Cyan
    try {
        winget install --id Microsoft.OpenJDK.21 -e --silent --accept-source-agreements --accept-package-agreements
        # Refresh environment PATH
        $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
        Write-Host "[+] OpenJDK 21 installed successfully." -ForegroundColor Green
    } catch {
        Write-Host "[!] Winget install failed. Downloading Adoptium OpenJDK 21 installer..." -ForegroundColor Yellow
        $installerUrl = "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.2%2B13/OpenJDK21U-jdk_x64_windows_hotspot_21.0.2_13.msi"
        $installerPath = "$env:TEMP\openjdk21.msi"
        Invoke-WebRequest -Uri $installerUrl -OutFile $installerPath -UseBasicParsing
        Start-Process msiexec.exe -ArgumentList "/i `"$installerPath`" /qn ADDLOCAL=FeatureMain,FeatureEnvironment,FeatureJarFileRunWith" -Wait
        $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
        Write-Host "[+] Adoptium OpenJDK 21 installed." -ForegroundColor Green
    }
}

# 3. Target Installation Directory
$installDir = "$env:LOCALAPPDATA\OpenHomeo"
if (-not (Test-Path $installDir)) {
    New-Item -ItemType Directory -Path $installDir -Force | Out-Null
}
Write-Host "[*] Target installation folder: $installDir" -ForegroundColor Gray

# 4. Release Bundle / Repository Fetching
$releaseZipUrl = "https://github.com/bkoustoov-oss/openhomeo/releases/download/v1.1.0/OpenHomeo-v1.1.0-Windows.zip"
$repoUrl = "https://github.com/bkoustoov-oss/openhomeo.git"
$downloadedRelease = $false

try {
    Write-Host "[*] Checking for pre-compiled OpenHomeo v1.1.0 release bundle on GitHub..." -ForegroundColor Cyan
    $zipPath = "$env:TEMP\openhomeo_v1.1.0_win.zip"
    Invoke-WebRequest -Uri $releaseZipUrl -OutFile $zipPath -UseBasicParsing -TimeoutSec 30
    Write-Host "[*] Extracting standalone binaries into $installDir..." -ForegroundColor Cyan
    Expand-Archive -Path $zipPath -DestinationPath $installDir -Force
    $downloadedRelease = $true
    Write-Host "[+] Pre-compiled release bundle installed successfully." -ForegroundColor Green
} catch {
    Write-Host "[-] Pre-compiled release download unavailable; falling back to GitHub source deployment..." -ForegroundColor Yellow
}

if (-not $downloadedRelease) {
    if (Get-Command git -ErrorAction SilentlyContinue) {
        if (Test-Path "$installDir\.git") {
            Write-Host "[*] Existing OpenHomeo repository detected. Pulling latest updates..." -ForegroundColor Cyan
            git -C $installDir pull
        } else {
            Write-Host "[*] Cloning OpenHomeo from GitHub repository..." -ForegroundColor Cyan
            git clone $repoUrl $installDir
        }
    } else {
        Write-Host "[*] Git not found. Downloading repository archive ZIP directly..." -ForegroundColor Cyan
        $zipPath = "$env:TEMP\openhomeo_master.zip"
        $zipUrl = "https://github.com/bkoustoov-oss/openhomeo/archive/refs/heads/master.zip"
        Invoke-WebRequest -Uri $zipUrl -OutFile $zipPath -UseBasicParsing
        Expand-Archive -Path $zipPath -DestinationPath "$env:TEMP\openhomeo_unzip" -Force
        Copy-Item -Path "$env:TEMP\openhomeo_unzip\openhomeo-master\*" -Destination $installDir -Recurse -Force
    }

    # 5. Build native Rust core and Java Application (Source mode)
    Push-Location $installDir
    try {
        # Check if native DLL is built
        $dllPath = "$installDir\native-core\target\release\openhomeo_native.dll"
        if (-not (Test-Path $dllPath)) {
            if (Get-Command cargo -ErrorAction SilentlyContinue) {
                Write-Host "[*] Compiling high-performance native Rust core via Cargo..." -ForegroundColor Cyan
                Push-Location "$installDir\native-core"
                cargo build --release
                Pop-Location
            }
        }

        # Build Maven Java workstation
        if (Get-Command mvn -ErrorAction SilentlyContinue) {
            Write-Host "[*] Compiling OpenHomeo workstation reactor via Maven..." -ForegroundColor Cyan
            mvn clean package -DskipTests
        }
    } finally {
        Pop-Location
    }
}

# 6. Create Desktop Shortcut and Start Menu Launcher
$desktopPath = [System.Environment]::GetFolderPath([System.Environment+SpecialFolder]::Desktop)
$shortcutPath = "$desktopPath\OpenHomeo Workstation.lnk"
$wsh = New-Object -ComObject WScript.Shell
$shortcut = $wsh.CreateShortcut($shortcutPath)
if (Test-Path "$installDir\openhomeo.bat") {
    $shortcut.TargetPath = "$installDir\openhomeo.bat"
    $shortcut.Arguments = ""
} else {
    $shortcut.TargetPath = "powershell.exe"
    $shortcut.Arguments = "-NoExit -Command `"cd '$installDir'; mvn javafx:run -pl openhomeo-gui`""
}
$shortcut.WorkingDirectory = $installDir
$shortcut.Description = "OpenHomeo - Classical Homeopathy Professional Workstation (FOSS)"
$shortcut.Save()

# 7. Create Start Script runner (run-openhomeo.cmd)
$cmdRunner = @"
@echo off
title OpenHomeo Workstation
cd /d "$installDir"
if exist "openhomeo.bat" (
    call openhomeo.bat
) else (
    mvn javafx:run -pl openhomeo-gui
)
"@
$cmdRunner | Out-File -FilePath "$installDir\run-openhomeo.cmd" -Encoding ascii

Write-Host @"

================================================================================
[+] OpenHomeo Workstation v1.1.0 has been deployed successfully!
================================================================================
  • Desktop Shortcut: $shortcutPath
  • Launcher Script:  $installDir\run-openhomeo.cmd
  • Clinic Database:  $env:USERPROFILE\.openhomeo\user_clinic.db (AES-256 Encrypted)
  • Bedside LAN App:  http://localhost:8090 (PIN: doctor123 / admin123)

To launch OpenHomeo immediately:
  Double-click the desktop shortcut or run:
  $installDir\run-openhomeo.cmd
"@ -ForegroundColor Green
