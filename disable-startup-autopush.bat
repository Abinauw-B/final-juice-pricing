@echo off
setlocal
echo ================================================================
echo 🛑 JUICE DYNAMIC PRICING - DISABLE WINDOWS STARTUP AUTO-PUSH
echo ================================================================

powershell.exe -ExecutionPolicy Bypass -Command ^
  "$startupFolder = [System.IO.Path]::Combine($env:APPDATA, 'Microsoft\Windows\Start Menu\Programs\Startup');" ^
  "$shortcutPath = [System.IO.Path]::Combine($startupFolder, 'JuiceAutoGitPush.lnk');" ^
  "if (Test-Path $shortcutPath) {" ^
  "  Remove-Item $shortcutPath -Force;" ^
  "  Write-Host '✅ Startup auto-push shortcut removed.' -ForegroundColor Green;" ^
  "} else {" ^
  "  Write-Host 'ℹ️ No startup auto-push shortcut was installed.' -ForegroundColor Yellow;" ^
  "}"

echo ================================================================
