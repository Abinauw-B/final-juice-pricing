@echo off
setlocal
powershell.exe -NoLogo -ExecutionPolicy Bypass -File "%~dp0scripts\stop-background.ps1" %*
