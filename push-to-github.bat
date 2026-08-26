@echo off
setlocal enabledelayedexpansion

echo ===================================================
echo 🚀 JUICE DYNAMIC PRICING - GITHUB PUSH UTILITY
echo Repository: https://github.com/Abinauw-B/final-juice-pricing.git
echo ===================================================

set "COMMIT_MSG=%~1"
if "%COMMIT_MSG%"=="" (
    set /p "COMMIT_MSG=Enter commit message (or press ENTER for default): "
)
if "!COMMIT_MSG!"=="" (
    set "COMMIT_MSG=update: project changes sync"
)

echo.
echo [1/3] Staging changes...
git add .

echo [2/3] Committing changes with message: "!COMMIT_MSG!"
git commit -m "!COMMIT_MSG!"

echo [3/3] Pushing to origin main...
git push origin main

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ✅ SUCCESS: All changes pushed to GitHub!
) else (
    echo.
    echo ❌ ERROR: Failed to push to GitHub. Please check your network and credentials.
)

echo ===================================================
pause
