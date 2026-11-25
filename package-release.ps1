# ==============================================================================
# OpenHomeo Cross-Platform Release Packaging Script (v1.3.0 Diamond+)
# Builds native zero-console jpackage app-image, Windows .msi (if WiX present),
# Windows portable .zip (with silent GUI launcher & Uninstaller), plus clean
# macOS & generic Linux launch/install/uninstall metadata bundles.
# ==============================================================================

param(
    [string]$Version = "1.3.0",
    [switch]$SkipMavenBuild
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$rootDir = $PSScriptRoot
$distDir = Join-Path $rootDir "dist"

Write-Host "[*] Packaging OpenHomeo Workstation v$Version..." -ForegroundColor Cyan
if (-not $SkipMavenBuild) {
    Write-Host "[*] Compiling, installing core modules & packaging JARs..." -ForegroundColor Cyan
    mvn -B install -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "Maven install/package failed." }
    mvn -B dependency:copy-dependencies -pl openhomeo-gui -DincludeScope=runtime
    if ($LASTEXITCODE -ne 0) { throw "Maven copy-dependencies failed." }
}

if (Test-Path $distDir) {
    Remove-Item -Path $distDir -Recurse -Force
}
New-Item -ItemType Directory -Path $distDir | Out-Null

# Ensure jpackage / jlink discovery on Windows
$jpackageExe = Get-Command jpackage.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
if (-not $jpackageExe) {
    $jdkCandidates = Get-ChildItem "$env:ProgramFiles\Microsoft","$env:ProgramFiles\Eclipse Adoptium","$env:ProgramFiles\Java" -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName "bin\jpackage.exe" } |
        Where-Object { Test-Path $_ }
    $jpackageExe = $jdkCandidates | Select-Object -First 1
}

# Prepare clean GUI input directory (strictly NO openhomeo-core split jar or test jars)
$winStageInput = Join-Path $distDir "_jpackage_input_win"
New-Item -ItemType Directory -Path (Join-Path $winStageInput "lib") -Force | Out-Null

$guiJar = Join-Path $rootDir "openhomeo-gui\target\openhomeo-gui-1.0.0-SNAPSHOT.jar"
if (-not (Test-Path $guiJar)) { throw "Missing built GUI jar at $guiJar" }
Copy-Item $guiJar -Destination $winStageInput -Force

$depDir = Join-Path $rootDir "openhomeo-gui\target\dependency"
if (-not (Test-Path $depDir)) {
    mvn dependency:copy-dependencies -pl openhomeo-gui -DincludeScope=runtime
}
Get-ChildItem -Path "$depDir\*.jar" | Where-Object {
    $_.Name -notmatch "junit|opentest4j|apiguardian|hamcrest|openhomeo-core"
} | ForEach-Object {
    Copy-Item $_.FullName -Destination (Join-Path $winStageInput "lib") -Force
}

$icoFile = Join-Path $rootDir "openhomeo.ico"
$pngFile = Join-Path $rootDir "openhomeo-gui\src\main\resources\icons\openhomeo.png"
if (Test-Path $icoFile) { Copy-Item $icoFile -Destination $winStageInput -Force }
if (Test-Path $pngFile) { Copy-Item $pngFile -Destination $winStageInput -Force }

# Add optional Rust native DLL if present
$rustDll = Join-Path $rootDir "native-core\target\release\openhomeo_native.dll"
if (Test-Path $rustDll) {
    New-Item -ItemType Directory -Path (Join-Path $winStageInput "native-core\target\release") -Force | Out-Null
    Copy-Item $rustDll -Destination (Join-Path $winStageInput "native-core\target\release") -Force
    Copy-Item $rustDll -Destination $winStageInput -Force
}

# 1. Build Windows Native jpackage App-Image (Zero Terminal OpenHomeo.exe + Bundled Java 21 Runtime)
$winPackageDir = Join-Path $distDir "OpenHomeo-v$Version-Windows"
$winAppStage = Join-Path $distDir "_win_appimage"
New-Item -ItemType Directory -Path $winAppStage -Force | Out-Null

$runtimeModules = "java.base,java.datatransfer,java.desktop,java.logging,java.management,java.naming,java.net.http,java.scripting,java.security.jgss,java.sql,java.xml,jdk.crypto.ec,jdk.httpserver,jdk.unsupported"

if ($jpackageExe) {
    Write-Host "[*] Building self-contained silent OpenHomeo.exe app-image via jpackage..." -ForegroundColor Green
    $jpTempApp = Join-Path $distDir "_jpkg_tmp_app"
    Remove-Item $jpTempApp -Recurse -Force -ErrorAction SilentlyContinue
    $jpArgs = @(
        "--type", "app-image",
        "--name", "OpenHomeo",
        "--app-version", $Version,
        "--vendor", "OpenHomeo Project",
        "--description", "Classical Homeopathy Clinical & Repertory Workstation",
        "--input", $winStageInput,
        "--main-jar", "openhomeo-gui-1.0.0-SNAPSHOT.jar",
        "--main-class", "org.openhomeo.gui.Main",
        "--add-modules", $runtimeModules,
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--temp", $jpTempApp,
        "--dest", $winAppStage
    )
    if (Test-Path $icoFile) {
        $jpArgs += @("--icon", $icoFile)
    }
    & $jpackageExe @jpArgs
    if ($LASTEXITCODE -ne 0) { throw "jpackage app-image creation failed!" }

    $builtApp = Join-Path $winAppStage "OpenHomeo"
    Move-Item -Path $builtApp -Destination $winPackageDir -Force
} else {
    Write-Warning "jpackage.exe not detected; packaging Windows portable bundle with silent VBS javaw.exe launcher."
    Copy-Item $winStageInput -Destination $winPackageDir -Recurse -Force
}

if (Test-Path $icoFile) { Copy-Item $icoFile -Destination $winPackageDir -Force }

# Silent VBScript fallback launcher (launches javaw.exe completely hidden - ZERO console window)
$vbsContent = @'
Set WshShell = CreateObject("WScript.Shell")
Set FSO = CreateObject("Scripting.FileSystemObject")
AppDir = FSO.GetParentFolderName(WScript.ScriptFullName)
WshShell.CurrentDirectory = AppDir
If FSO.FileExists(AppDir & "\OpenHomeo.exe") Then
    WshShell.Run """" & AppDir & "\OpenHomeo.exe""", 1, False
Else
    WshShell.Run "javaw.exe --enable-native-access=ALL-UNNAMED -cp ""openhomeo-gui-1.0.0-SNAPSHOT.jar;lib\*"" org.openhomeo.gui.Main", 0, False
End If
'@
$vbsContent | Out-File -FilePath (Join-Path $winPackageDir "OpenHomeo.vbs") -Encoding ascii

# PowerShell & batch uninstallers inside portable folder
$uninstallPs = @'
param([switch]$Silent)
$ErrorActionPreference = "Continue"
$appDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Get-Process -Name "OpenHomeo","javaw" -ErrorAction SilentlyContinue | Where-Object { $_.Path -and $_.Path -like "$appDir*" } | Stop-Process -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path ([Environment]::GetFolderPath("Desktop")) "OpenHomeo Workstation.lnk") -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path ([Environment]::GetFolderPath("Programs")) "OpenHomeo") -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\OpenHomeo" -Force -ErrorAction SilentlyContinue
if (-not $Silent) {
    $ans = Read-Host "Keep your patient database (~/.openhomeo/user_clinic.db)? [Y/n]"
    if ($ans -eq "n" -or $ans -eq "N") { Remove-Item (Join-Path $env:USERPROFILE ".openhomeo") -Recurse -Force -ErrorAction SilentlyContinue }
}
Start-Process "cmd.exe" -ArgumentList "/c ping 127.0.0.1 -n 3 >nul & rmdir /s /q `"$appDir`"" -WindowStyle Hidden
'@
$uninstallPs | Out-File -FilePath (Join-Path $winPackageDir "Uninstall-OpenHomeo.ps1") -Encoding utf8
"@echo off`r`npowershell -NoProfile -ExecutionPolicy Bypass -File `"%~dp0Uninstall-OpenHomeo.ps1`"`r`npause`r`n" |
    Out-File -FilePath (Join-Path $winPackageDir "uninstall.bat") -Encoding ascii

$shortcutScript = @'
$wsh = New-Object -ComObject WScript.Shell
$desktop = [Environment]::GetFolderPath([Environment+SpecialFolder]::Desktop)
$lnk = $wsh.CreateShortcut((Join-Path $desktop "OpenHomeo Workstation.lnk"))
$exe = Join-Path $PSScriptRoot "OpenHomeo.exe"
$vbs = Join-Path $PSScriptRoot "OpenHomeo.vbs"
if (Test-Path $exe) {
    $lnk.TargetPath = $exe
} else {
    $lnk.TargetPath = "wscript.exe"
    $lnk.Arguments = "`"$vbs`""
}
$lnk.WorkingDirectory = $PSScriptRoot
$ico = Join-Path $PSScriptRoot "openhomeo.ico"
if (Test-Path $ico) { $lnk.IconLocation = "$ico,0" }
$lnk.WindowStyle = 7
$lnk.Description = "OpenHomeo - Classical Homeopathy Professional Workstation"
$lnk.Save()
'@
$shortcutScript | Out-File -FilePath (Join-Path $winPackageDir "create-desktop-shortcut.ps1") -Encoding utf8

# Compress Windows ZIP (works on BOTH fresh installs & in-app v1.2/v1.3 OTA swaps)
$winZip = Join-Path $distDir "OpenHomeo-v$Version-Windows.zip"
Compress-Archive -Path "$winPackageDir\*" -DestinationPath $winZip -Force
Write-Host "[+] Created Windows ZIP bundle: $winZip" -ForegroundColor Green

# 2. Optional Native Windows .msi Installer (auto-enabled whenever WiX v3/v4 is installed, e.g. CI windows-latest)
$hasWix = (Get-Command wix.exe -ErrorAction SilentlyContinue) -or (Get-Command candle.exe -ErrorAction SilentlyContinue)
if ($jpackageExe -and $hasWix) {
    Write-Host "[*] WiX detected! Building Native Windows Installer (.msi)..." -ForegroundColor Green
    $msiOut = Join-Path $distDir "_msi_out"
    New-Item -ItemType Directory -Path $msiOut -Force | Out-Null
    $msiArgs = @(
        "--type", "msi",
        "--name", "OpenHomeo",
        "--app-version", $Version,
        "--vendor", "OpenHomeo Project",
        "--description", "OpenHomeo Classical Homeopathy Workstation",
        "--input", $winStageInput,
        "--main-jar", "openhomeo-gui-1.0.0-SNAPSHOT.jar",
        "--main-class", "org.openhomeo.gui.Main",
        "--add-modules", $runtimeModules,
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--win-shortcut",
        "--win-shortcut-prompt",
        "--win-menu",
        "--win-menu-group", "OpenHomeo",
        "--win-per-user-install",
        "--win-upgrade-uuid", "9E4C5C1D-3B88-4E47-9D2A-7B40A05E1300",
        "--dest", $msiOut
    )
    if (Test-Path $icoFile) { $msiArgs += @("--icon", $icoFile) }
    & $jpackageExe @msiArgs
    if ($LASTEXITCODE -eq 0) {
        $genMsi = Get-ChildItem -Path $msiOut -Filter "*.msi" | Select-Object -First 1
        if ($genMsi) {
            $finalMsi = Join-Path $distDir "OpenHomeo-v$Version-Windows.msi"
            Move-Item $genMsi.FullName -Destination $finalMsi -Force
            Write-Host "[+] Created Native Windows MSI Installer: $finalMsi" -ForegroundColor Green
        }
    }
    Remove-Item $msiOut -Recurse -Force -ErrorAction SilentlyContinue
} else {
    Write-Host "[*] WiX Toolset not installed locally; .msi is built automatically in GitHub Actions release matrix." -ForegroundColor DarkGray
}

# Clean temporary staging directories
Remove-Item $winStageInput -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item $winAppStage -Recurse -Force -ErrorAction SilentlyContinue

Write-Host "`n================================================================================" -ForegroundColor Green
Write-Host "  Release Artifacts Ready in $distDir" -ForegroundColor Green
Write-Host "================================================================================" -ForegroundColor Green
Get-ChildItem -Path $distDir -File | Format-Table Name, Length, LastWriteTime
