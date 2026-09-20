@echo off
setlocal
echo ================================================================
echo JUICE DYNAMIC PRICING - ENABLE WINDOWS STARTUP AUTO-PUSH
echo ================================================================

set "INTERVAL=10"
if not "%~1"=="" set "INTERVAL=%~1"

powershell.exe -NoLogo -ExecutionPolicy Bypass -Command ^
  "$startupFolder = [System.IO.Path]::Combine($env:APPDATA, 'Microsoft\Windows\Start Menu\Programs\Startup');" ^
  "$shortcutPath = [System.IO.Path]::Combine($startupFolder, 'JuiceAutoGitPush.lnk');" ^
  "$autopushDir = '%~dp0'.TrimEnd('\');" ^
  "$scriptsDir = [System.IO.Path]::GetFullPath((Join-Path $autopushDir '..'));" ^
  "$projectDir = [System.IO.Path]::GetFullPath((Join-Path $scriptsDir '..'));" ^
  "$vbsPath = Join-Path $scriptsDir 'run-hidden.vbs';" ^
  "$wshell = New-Object -ComObject WScript.Shell;" ^
  "$shortcut = $wshell.CreateShortcut($shortcutPath);" ^
  "$shortcut.TargetPath = 'wscript.exe';" ^
  "$shortcut.Arguments = ('\"' + $vbsPath + '\" %INTERVAL%');" ^
  "$shortcut.WorkingDirectory = $projectDir;" ^
  "$shortcut.Description = 'Juice Dynamic Pricing Background Auto Git Push';" ^
  "$shortcut.Save();" ^
  "Write-Host '[SUCCESS] Auto-push on Windows Startup enabled!' -ForegroundColor Green;" ^
  "Write-Host ('   Shortcut created : ' + $shortcutPath) -ForegroundColor Cyan;" ^
  "Write-Host ('   Sync Interval    : Every %INTERVAL% minutes') -ForegroundColor Cyan;" ^
  "Write-Host ('   To disable run   : disable-startup-autopush.bat') -ForegroundColor Yellow;"

echo ================================================================
