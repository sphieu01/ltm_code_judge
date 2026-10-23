@echo off
title Start Ngrok Tunnel - Auto Grader Server
cd /d "%~dp0"

echo ====================================================
echo   Khoi chay Tunnel Ngrok cho Auto Grader Server
echo ====================================================
echo.
echo Server dang lang nghe tren port 9999...
echo Dang mo tunnel TCP ra Internet...
echo.

"C:\Users\Admin\AppData\Local\Microsoft\WinGet\Packages\Ngrok.Ngrok_Microsoft.Winget.Source_8wekyb3d8bbwe\ngrok.exe" tcp 9999
pause
