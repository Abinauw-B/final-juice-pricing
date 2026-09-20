@echo off
title Pub Exchange Cross-Panel Server Launcher
color 0A
echo =========================================================================
echo  NOIDA PUB EXCHANGE - DYNAMIC BEVERAGE STOCK MARKET PLATFORM
echo =========================================================================
echo  Starting Spring Boot Backend Server on http://localhost:8088 ...
echo  Starting Customer Web Server on http://localhost:8000 ...
echo  Starting Admin Panel Server on http://localhost:8001 ...
echo.

cd /d "%~dp0"
start "Spring Boot Backend (Port 8088)" cmd /k "cd backend && mvnw spring-boot:run"
start "Customer Web (Port 8000)" cmd /k "npm --prefix customer-web start"
start "Admin Panel (Port 8001)" cmd /k "npm --prefix admin-panel start"

pause
