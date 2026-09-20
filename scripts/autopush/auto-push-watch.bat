@echo off
setlocal
title Juice Dynamic Pricing - Git Auto-Push Watcher
cls
echo ================================================================
echo 🚀 JUICE DYNAMIC PRICING - GIT AUTO-PUSH WATCHER
echo ================================================================
echo This terminal monitors your project and automatically pushes
echo any new changes to GitHub at regular intervals.
echo.
echo Target Repo: https://github.com/Abinauw-B/final-juice-pricing.git
echo Interval:    Every 10 minutes (default)
echo.
echo Press [Ctrl+C] at any time to stop the watcher.
echo ================================================================
echo.

set "INTERVAL=10"
if not "%~1"=="" set "INTERVAL=%~1"

powershell.exe -NoLogo -ExecutionPolicy Bypass -File "%~dp0scripts\auto-git-push.ps1" -IntervalMinutes %INTERVAL%

pause
