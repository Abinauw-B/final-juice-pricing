@echo off
setlocal
echo ================================================================
echo JUICE DYNAMIC PRICING - START BACKGROUND AUTO-PUSH
echo ================================================================

set "INTERVAL=10"
if not "%~1"=="" set "INTERVAL=%~1"

wscript.exe "%~dp0scripts\run-hidden.vbs" %INTERVAL%

echo [SUCCESS] Auto-push background daemon initiated!
echo    Sync Interval : Every %INTERVAL% minute(s)
echo    Target Remote : GitHub (origin/main)
echo    Log File      : scripts\.autopush.log
echo    To view logs  : type scripts\.autopush.log
echo    To stop it run: stop-autopush.bat
echo ================================================================
