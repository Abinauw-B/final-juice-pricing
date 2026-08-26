@echo off
setlocal enabledelayedexpansion

echo ================================================================
echo 🚀 JUICE DYNAMIC PRICING - GITHUB SYNC ^& PUSH UTILITY
echo Target Repo: https://github.com/Abinauw-B/final-juice-pricing.git
echo ================================================================

:: Check for uncommitted changes
git status --porcelain > "%TEMP%\git_status.tmp"
for %%A in ("%TEMP%\git_status.tmp") do set size=%%~zA

set "COMMIT_MSG=%~1"

if %size% GTR 0 (
    echo.
    echo 📋 Detected local file changes.
    if "!COMMIT_MSG!"=="" (
        set /p "COMMIT_MSG=Enter commit message (or press ENTER for default): "
    )
    if "!COMMIT_MSG!"=="" (
        set "COMMIT_MSG=update: sync project changes and pricing engine"
    )

    echo.
    echo [1/3] Staging all files (git add .)...
    git add .

    echo [2/3] Committing changes with message: "!COMMIT_MSG!"...
    git commit -m "!COMMIT_MSG!"
) else (
    echo.
    echo ℹ️ No new uncommitted changes detected. Proceeding to push unpushed commits...
)

del "%TEMP%\git_status.tmp" 2>nul

echo.
echo [3/3] Syncing ^& Pushing to origin main...
git pull --rebase origin main
git push origin main

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ================================================================
    echo ✅ SUCCESS: Repository is up to date with GitHub!
    echo ================================================================
) else (
    echo.
    echo ================================================================
    echo ❌ ERROR: Push failed. Check your internet connection or Git credentials.
    echo ================================================================
)

echo.
pause
