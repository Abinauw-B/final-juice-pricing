@echo off
setlocal
echo ================================================================
echo 🚀 JUICE DYNAMIC PRICING - START BACKGROUND AUTO-PUSH
echo ================================================================

set "INTERVAL=10"
if not "%~1"=="" set "INTERVAL=%~1"

powershell.exe -ExecutionPolicy Bypass -Command ^
  "$pidFile = Join-Path '%~dp0scripts' '.autopush.pid';" ^
  "if (Test-Path $pidFile) {" ^
  "  $oldPid = Get-Content $pidFile -ErrorAction SilentlyContinue;" ^
  "  if ($oldPid -and (Get-Process -Id $oldPid -ErrorAction SilentlyContinue)) {" ^
  "    Write-Host 'ℹ️ Auto-push is ALREADY running in background (PID: ' $oldPid ')' -ForegroundColor Yellow;" ^
  "    exit 0;" ^
  "  }" ^
  "}" ^
  "$scriptPath = Join-Path '%~dp0scripts' 'auto-git-push.ps1';" ^
  "$p = Start-Process powershell.exe -ArgumentList '-NoLogo', '-ExecutionPolicy', 'Bypass', '-WindowStyle', 'Hidden', '-File', ('\"' + $scriptPath + '\"'), '-IntervalMinutes', '%INTERVAL%', '-Quiet' -PassThru;" ^
  "$p.Id | Out-File -FilePath $pidFile -Encoding ascii -Force;" ^
  "Write-Host '✅ Auto-push background service started successfully!' -ForegroundColor Green;" ^
  "Write-Host '   Process ID (PID)  : ' $p.Id -ForegroundColor Cyan;" ^
  "Write-Host '   Sync Interval     : Every %INTERVAL% minutes' -ForegroundColor Cyan;" ^
  "Write-Host '   Live Log File     : scripts\.autopush.log' -ForegroundColor Cyan;" ^
  "Write-Host '   To stop it run    : stop-autopush.bat' -ForegroundColor Yellow;"

echo ================================================================
