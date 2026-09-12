@echo off
setlocal
echo ================================================================
echo 🚀 JUICE DYNAMIC PRICING - ENABLE WINDOWS STARTUP AUTO-PUSH
echo ================================================================

set "INTERVAL=10"
if not "%~1"=="" set "INTERVAL=%~1"

powershell.exe -ExecutionPolicy Bypass -Command ^
  "$startupFolder = [System.IO.Path]::Combine($env:APPDATA, 'Microsoft\Windows\Start Menu\Programs\Startup');" ^
  "$shortcutPath = [System.IO.Path]::Combine($startupFolder, 'JuiceAutoGitPush.lnk');" ^
  "$projectDir = '%~dp0'.TrimEnd('\');" ^
  "$scriptPath = Join-Path $projectDir 'scripts\auto-git-push.ps1';" ^
  "$wshell = New-Object -ComObject WScript.Shell;" ^
  "$shortcut = $wshell.CreateShortcut($shortcutPath);" ^
  "$shortcut.TargetPath = 'powershell.exe';" ^
  "$shortcut.Arguments = ('-NoLogo -ExecutionPolicy Bypass -WindowStyle Hidden -File \"' + $scriptPath + '\" -IntervalMinutes %INTERVAL% -Quiet');" ^
  "$shortcut.WorkingDirectory = $projectDir;" ^
  "$shortcut.Description = 'Juice Dynamic Pricing Background Auto Git Push';" ^
  "$shortcut.Save();" ^
  "Write-Host '✅ Auto-push on Windows Startup enabled!' -ForegroundColor Green;" ^
  "Write-Host '   Shortcut created : ' $shortcutPath -ForegroundColor Cyan;" ^
  "Write-Host '   Sync Interval    : Every %INTERVAL% minutes' -ForegroundColor Cyan;" ^
  "Write-Host '   To disable run   : disable-startup-autopush.bat' -ForegroundColor Yellow;"

echo ================================================================
