@echo off
title OpenHomeo Professional Workstation
cd /d "%~dp0"
echo ===================================================
echo   Launching OpenHomeo Classical Workstation...
echo ===================================================
mvn javafx:run -pl openhomeo-gui
pause
