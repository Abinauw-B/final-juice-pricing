@echo off
setlocal
echo ================================================================
echo 🛑 JUICE DYNAMIC PRICING - STOP BACKGROUND AUTO-PUSH
echo ================================================================

powershell.exe -ExecutionPolicy Bypass -Command ^
  "$pidFile = Join-Path '%~dp0scripts' '.autopush.pid';" ^
  "$stopped = $false;" ^
  "if (Test-Path $pidFile) {" ^
  "  $targetPid = Get-Content $pidFile -ErrorAction SilentlyContinue;" ^
  "  if ($targetPid) {" ^
  "    try {" ^
  "      Stop-Process -Id $targetPid -Force -ErrorAction SilentlyContinue;" ^
  "      Write-Host '✅ Stopped auto-push background process (PID: ' $targetPid ')' -ForegroundColor Green;" ^
  "      $stopped = $true;" ^
  "    } catch {}" ^
  "  }" ^
  "  Remove-Item $pidFile -Force -ErrorAction SilentlyContinue;" ^
  "}" ^
  "$procs = Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*auto-git-push.ps1*' };" ^
  "foreach ($proc in $procs) {" ^
  "  try {" ^
  "    Stop-Process -Id $proc.ProcessId -Force -ErrorAction SilentlyContinue;" ^
  "    Write-Host '✅ Stopped stray auto-push process (PID: ' $proc.ProcessId ')' -ForegroundColor Green;" ^
  "    $stopped = $true;" ^
  "  } catch {}" ^
  "}" ^
  "if (-not $stopped) {" ^
  "  Write-Host 'ℹ️ No active auto-push background service found.' -ForegroundColor Yellow;" ^
  "}"

echo ================================================================
