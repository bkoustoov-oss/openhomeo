# ==============================================================================
# OpenHomeo Workstation - Silent One-Line Windows Installer & Uninstaller Setup
# Usage: irm https://raw.githubusercontent.com/bkoustoov-oss/openhomeo/master/install.ps1 | iex
# ==============================================================================

$ErrorActionPreference = "Continue"
$ProgressPreference = "SilentlyContinue"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "  OpenHomeo Classical Homeopathy Workstation - Automated Windows Setup          " -ForegroundColor Green
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host ""

$installDir = Join-Path $env:LOCALAPPDATA "OpenHomeo"
$tag = "v1.3.0"
$repoBase = "https://github.com/bkoustoov-oss/openhomeo"
$msiUrl = "$repoBase/releases/download/$tag/OpenHomeo-$tag-Windows.msi"
$zipUrl = "$repoBase/releases/download/$tag/OpenHomeo-$tag-Windows.zip"
$manifestUrl = "https://raw.githubusercontent.com/bkoustoov-oss/openhomeo/master/ota_manifest.json"

function Get-JavaMajorVersion {
    param([string]$JavaExe)
    try {
        $prevPref = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $out = cmd /c "`"$JavaExe`" -version 2>&1"
        $ErrorActionPreference = $prevPref
        $joined = ($out -join "`n")
        if ($joined -match 'version\s+"(\d+)(?:[\._](\d+))?[^"]*"') {
            $first = [int]$matches[1]
            if ($first -eq 1 -and $matches[2]) {
                return [int]$matches[2]
            }
            return $first
        }
    } catch {}
    return 0
}

function Find-Java21Plus {
    $candidates = New-Object System.Collections.Generic.List[string]
    $cmdJava = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($cmdJava) { $candidates.Add($cmdJava.Source) }
    if ($env:JAVA_HOME) {
        $jh = Join-Path $env:JAVA_HOME "bin\java.exe"
        if (Test-Path $jh) { $candidates.Add($jh) }
    }
    $searchRoots = @(
        (Join-Path $env:ProgramFiles "Microsoft"),
        (Join-Path $env:ProgramFiles "Eclipse Adoptium"),
        (Join-Path $env:ProgramFiles "Java"),
        (Join-Path $env:ProgramFiles "Zulu"),
        (Join-Path $env:ProgramFiles "BellSoft"),
        (Join-Path $env:LOCALAPPDATA "Programs\Eclipse Adoptium")
    )
    foreach ($root in $searchRoots) {
        if ($root -and (Test-Path $root)) {
            Get-ChildItem -Path $root -Directory -ErrorAction SilentlyContinue | ForEach-Object {
                $candExe = Join-Path $_.FullName "bin\java.exe"
                if (Test-Path $candExe) { $candidates.Add($candExe) }
            }
        }
    }
    foreach ($exe in ($candidates | Select-Object -Unique)) {
        if (Test-Path $exe) {
            $maj = Get-JavaMajorVersion -JavaExe $exe
            if ($maj -ge 21) {
                return [PSCustomObject]@{ JavaExe = $exe; JavawExe = ($exe -replace 'java\.exe$', 'javaw.exe'); Major = $maj }
            }
        }
    }
    return $null
}

# 0. Resolve latest tag dynamically from ota_manifest.json when reachable
try {
    $manifest = Invoke-RestMethod -Uri $manifestUrl -TimeoutSec 8 -Headers @{ "User-Agent" = "OpenHomeo-Installer" }
    if ($manifest.semver) {
        $tag = "v" + $manifest.semver
        $msiUrl = "$repoBase/releases/download/$tag/OpenHomeo-$tag-Windows.msi"
        $zipUrl = "$repoBase/releases/download/$tag/OpenHomeo-$tag-Windows.zip"
    }
    if ($manifest.downloadUrls.windowsMsi) { $msiUrl = $manifest.downloadUrls.windowsMsi }
    if ($manifest.downloadUrls.windows) { $zipUrl = $manifest.downloadUrls.windows }
} catch {
    Write-Host "[*] Using release channel $tag..." -ForegroundColor DarkGray
}

# 1. Check Java 21+ non-destructively (do NOT fail on stderr from java -version)
Write-Host "[1/4] Checking Java 21+ LTS runtime..." -ForegroundColor Yellow
$javaInfo = Find-Java21Plus
if ($javaInfo) {
    Write-Host "[+] Detected Java $($javaInfo.Major) LTS cleanly at: $($javaInfo.JavaExe)" -ForegroundColor Green
} else {
    Write-Host "[*] Java 21 not found on PATH; attempting optional Winget install (OpenHomeo bundled runtime also works standalone)..." -ForegroundColor Yellow
    if (Get-Command winget -ErrorAction SilentlyContinue) {
        try {
            winget install --id Microsoft.OpenJDK.21 --exact --accept-package-agreements --accept-source-agreements --silent | Out-Null
            $javaInfo = Find-Java21Plus
        } catch {}
    }
    if ($javaInfo) {
        Write-Host "[+] Installed Java $($javaInfo.Major) LTS cleanly." -ForegroundColor Green
    } else {
        Write-Host "[*] Continuing with self-contained application runtime bundle." -ForegroundColor Cyan
    }
}

# 2. Prefer Native MSI Installer when hosted on release, otherwise install Portable App Bundle silently
$msiTemp = Join-Path $env:TEMP "OpenHomeo-$tag-Windows.msi"
$installedViaMsi = $false
Write-Host "[2/4] Downloading OpenHomeo $tag for Windows..." -ForegroundColor Yellow

try {
    Invoke-WebRequest -Uri $msiUrl -OutFile $msiTemp -UseBasicParsing -MaximumRedirection 10
    if ((Test-Path $msiTemp) -and ((Get-Item $msiTemp).Length -gt 5000000)) {
        Write-Host "[*] Downloaded Native Windows Installer (.msi). Launching installer..." -ForegroundColor Cyan
        $msiProc = Start-Process -FilePath "msiexec.exe" -ArgumentList @("/i", "`"$msiTemp`"", "/passive", "/norestart") -Wait -PassThru
        if ($msiProc.ExitCode -eq 0) {
            $installedViaMsi = $true
            Write-Host "[+] Native MSI installation completed cleanly (Start Menu & Add/Remove Programs registered)!" -ForegroundColor Green
        }
    }
} catch {
    Write-Host "[*] Native MSI asset unavailable or skipped; installing silent self-contained portable package..." -ForegroundColor DarkGray
} finally {
    Remove-Item $msiTemp -Force -ErrorAction SilentlyContinue
}

if (-not $installedViaMsi) {
    if (-not (Test-Path $installDir)) {
        New-Item -ItemType Directory -Path $installDir -Force | Out-Null
    }
    $zipTemp = Join-Path $env:TEMP "openhomeo_win_$tag.zip"
    try {
        Invoke-WebRequest -Uri $zipUrl -OutFile $zipTemp -UseBasicParsing -MaximumRedirection 10
    } catch {
        Write-Host "[!] Failed downloading OpenHomeo release package: $($_.Exception.Message)" -ForegroundColor Red
        exit 1
    }

    Write-Host "[3/4] Extracting OpenHomeo silently to $installDir (user_clinic.db preserved)..." -ForegroundColor Yellow
    $stageDir = Join-Path $env:TEMP "openhomeo_stage_$tag"
    if (Test-Path $stageDir) { Remove-Item $stageDir -Recurse -Force -ErrorAction SilentlyContinue }
    Expand-Archive -Path $zipTemp -DestinationPath $stageDir -Force
    Remove-Item $zipTemp -Force -ErrorAction SilentlyContinue

    $subDirs = Get-ChildItem -Path $stageDir -Directory
    $sourceRoot = $stageDir
    if ($subDirs.Count -eq 1 -and (Test-Path (Join-Path $subDirs[0].FullName "openhomeo-gui-1.0.0-SNAPSHOT.jar") -or Test-Path (Join-Path $subDirs[0].FullName "OpenHomeo.exe"))) {
        $sourceRoot = $subDirs[0].FullName
    }

    Get-ChildItem -Path $sourceRoot -Recurse | ForEach-Object {
        $rel = $_.FullName.Substring($sourceRoot.Length).TrimStart('\', '/')
        if ($rel -match 'user_clinic\.db|workstation_mode\.properties') { return }
        $dest = Join-Path $installDir $rel
        if ($_.PSIsContainer) {
            if (-not (Test-Path $dest)) { New-Item -ItemType Directory -Path $dest -Force | Out-Null }
        } else {
            $destParent = Split-Path -Parent $dest
            if (-not (Test-Path $destParent)) { New-Item -ItemType Directory -Path $destParent -Force | Out-Null }
            Copy-Item $_.FullName -Destination $dest -Force
        }
    }
    Remove-Item $stageDir -Recurse -Force -ErrorAction SilentlyContinue
    # Clean legacy split core jar if present
    Remove-Item (Join-Path $installDir "lib\openhomeo-core-*.jar") -Force -ErrorAction SilentlyContinue

    # Ensure zero-console VBS GUI launcher exists as backup whenever OpenHomeo.exe isn't present
    $javawCmd = if ($javaInfo -and (Test-Path $javaInfo.JavawExe)) { $javaInfo.JavawExe } else { "javaw.exe" }
    $vbsContent = @"
Set WshShell = CreateObject("WScript.Shell")
Set FSO = CreateObject("Scripting.FileSystemObject")
AppDir = FSO.GetParentFolderName(WScript.ScriptFullName)
WshShell.CurrentDirectory = AppDir
If FSO.FileExists(AppDir & "\openhomeo-gui-update.jar") Then
    On Error Resume Next
    FSO.CopyFile AppDir & "\openhomeo-gui-update.jar", AppDir & "\openhomeo-gui-1.0.0-SNAPSHOT.jar", True
    FSO.DeleteFile AppDir & "\openhomeo-gui-update.jar", True
    On Error GoTo 0
End If
Cmd = """$javawCmd"" --enable-native-access=ALL-UNNAMED -cp ""openhomeo-gui-1.0.0-SNAPSHOT.jar;lib\*"" org.openhomeo.gui.Main"
WshShell.Run Cmd, 0, False
"@
    $vbsPath = Join-Path $installDir "OpenHomeo.vbs"
    $vbsContent | Out-File -FilePath $vbsPath -Encoding ascii

    # Install clean Windows Uninstaller + Add/Remove Programs Registry registration
    $uninstallScript = @'
param([switch]$Silent)
$ErrorActionPreference = "Continue"
$appDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Write-Host "Uninstalling OpenHomeo Workstation from $appDir ..." -ForegroundColor Yellow

Get-Process -Name "OpenHomeo","javaw" -ErrorAction SilentlyContinue | Where-Object {
    $_.Path -and ($_.Path -like "$appDir*")
} | Stop-Process -Force -ErrorAction SilentlyContinue

$desktopLnk = Join-Path ([Environment]::GetFolderPath("Desktop")) "OpenHomeo Workstation.lnk"
$startMenuDir = Join-Path ([Environment]::GetFolderPath("Programs")) "OpenHomeo"
Remove-Item $desktopLnk -Force -ErrorAction SilentlyContinue
Remove-Item $startMenuDir -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\OpenHomeo" -Force -ErrorAction SilentlyContinue

if (-not $Silent) {
    $ans = Read-Host "Preserve your clinic patient database (~/.openhomeo/user_clinic.db)? [Y/n]"
    if ($ans -eq "n" -or $ans -eq "N") {
        Remove-Item (Join-Path $env:USERPROFILE ".openhomeo") -Recurse -Force -ErrorAction SilentlyContinue
        Write-Host "[*] Removed user data directory ~/.openhomeo." -ForegroundColor Gray
    }
}

$cmdCleanup = "ping 127.0.0.1 -n 3 >nul & rmdir /s /q `"$appDir`""
Start-Process "cmd.exe" -ArgumentList "/c $cmdCleanup" -WindowStyle Hidden
Write-Host "[+] OpenHomeo Workstation uninstalled cleanly." -ForegroundColor Green
'@
    $uninstallPs1 = Join-Path $installDir "Uninstall-OpenHomeo.ps1"
    $uninstallScript | Out-File -FilePath $uninstallPs1 -Encoding utf8
    $uninstallBat = "@echo off`r`npowershell -NoProfile -ExecutionPolicy Bypass -File `"%~dp0Uninstall-OpenHomeo.ps1`"`r`npause`r`n"
    $uninstallBat | Out-File -FilePath (Join-Path $installDir "uninstall.bat") -Encoding ascii

    $regPath = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\OpenHomeo"
    New-Item -Path $regPath -Force | Out-Null
    Set-ItemProperty -Path $regPath -Name "DisplayName" -Value "OpenHomeo Classical Workstation ($tag)"
    Set-ItemProperty -Path $regPath -Name "DisplayVersion" -Value ($tag.TrimStart('v'))
    Set-ItemProperty -Path $regPath -Name "Publisher" -Value "OpenHomeo Project"
    Set-ItemProperty -Path $regPath -Name "InstallLocation" -Value $installDir
    Set-ItemProperty -Path $regPath -Name "DisplayIcon" -Value (Join-Path $installDir "openhomeo.ico")
    Set-ItemProperty -Path $regPath -Name "UninstallString" -Value "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$uninstallPs1`" -Silent"
    Set-ItemProperty -Path $regPath -Name "NoModify" -Value 1 -Type DWord
    Set-ItemProperty -Path $regPath -Name "NoRepair" -Value 1 -Type DWord

    Write-Host "[4/4] Creating silent Desktop & Start Menu shortcuts (NO terminal window)..." -ForegroundColor Yellow
    $wsh = New-Object -ComObject WScript.Shell
    $exePath = Join-Path $installDir "OpenHomeo.exe"
    $targetLnk = if (Test-Path $exePath) { $exePath } else { "wscript.exe" }
    $targetArgs = if (Test-Path $exePath) { "" } else { "`"$vbsPath`"" }
    $icoPath = Join-Path $installDir "openhomeo.ico"

    $desktopLnk = Join-Path ([Environment]::GetFolderPath("Desktop")) "OpenHomeo Workstation.lnk"
    $startMenuDir = Join-Path ([Environment]::GetFolderPath("Programs")) "OpenHomeo"
    if (-not (Test-Path $startMenuDir)) { New-Item -ItemType Directory -Path $startMenuDir -Force | Out-Null }
    $startLnk = Join-Path $startMenuDir "OpenHomeo Workstation.lnk"

    foreach ($lnkPath in @($desktopLnk, $startLnk)) {
        $s = $wsh.CreateShortcut($lnkPath)
        $s.TargetPath = $targetLnk
        if ($targetArgs) { $s.Arguments = $targetArgs }
        $s.WorkingDirectory = $installDir
        if (Test-Path $icoPath) { $s.IconLocation = "$icoPath,0" }
        $s.WindowStyle = 7
        $s.Description = "OpenHomeo - Classical Homeopathy Professional Workstation"
        $s.Save()
    }

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
}

Write-Host ""
Write-Host "================================================================================" -ForegroundColor Green
Write-Host "  SUCCESS! OpenHomeo $tag installed silently with zero console popups!         " -ForegroundColor Green
Write-Host "================================================================================" -ForegroundColor Green
Write-Host "Launch from your Desktop shortcut ('OpenHomeo Workstation') or Start Menu." -ForegroundColor Cyan
Write-Host "Default Admin Login: admin / admin123" -ForegroundColor Gray
Write-Host "Launching OpenHomeo now..." -ForegroundColor Yellow

$exeLaunch = Join-Path $installDir "OpenHomeo.exe"
$vbsLaunch = Join-Path $installDir "OpenHomeo.vbs"
if (Test-Path $exeLaunch) {
    Start-Process -FilePath $exeLaunch -WorkingDirectory $installDir
} elseif (Test-Path $vbsLaunch) {
    Start-Process -FilePath "wscript.exe" -ArgumentList "`"$vbsLaunch`"" -WorkingDirectory $installDir -WindowStyle Hidden
}
