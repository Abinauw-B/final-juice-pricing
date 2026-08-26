@echo off
setlocal enabledelayedexpansion

echo ================================================================
echo 🚀 JUICE DYNAMIC PRICING - GITHUB SYNC ^& PUSH UTILITY
echo Target Repo: https://github.com/Abinauw-B/final-juice-pricing.git
echo ================================================================

set "COMMIT_MSG=%~1"
if "%COMMIT_MSG%"=="" (
    set "COMMIT_MSG=update: sync project changes and pricing engine"
)

echo.
echo [1/3] Staging all files (git add .)...
git add .

echo.
echo [2/3] Committing changes (if any)...
git commit -m "%COMMIT_MSG%" 2>nul || echo ℹ️ No new changes to commit.

echo.
echo [3/3] Syncing ^& Pushing to origin main...
git pull --rebase origin main
git push origin main

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ================================================================
    echo ✅ SUCCESS: All code and configuration pushed to GitHub repository!
    echo ================================================================
) else (
    echo.
    echo ================================================================
    echo ❌ PUSH FAILED: Please check internet connection or GitHub permissions.
    echo ================================================================
)
