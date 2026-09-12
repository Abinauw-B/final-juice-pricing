@echo off
setlocal
powershell.exe -NoLogo -ExecutionPolicy Bypass -File "%~dp0scripts\start-background.ps1" %*
