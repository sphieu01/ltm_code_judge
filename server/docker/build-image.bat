@echo off
REM ============================================================
REM build-image.bat — Build Docker Image ltm-judge-runner
REM ============================================================

echo.
echo ====================================================
echo   Building Docker Image: ltm-judge-runner:latest
echo ====================================================
echo.

cd /d "%~dp0"

docker build -t ltm-judge-runner:latest .

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ====================================================
    echo   [SUCCESS] Docker image ltm-judge-runner:latest
    echo   da duoc tao thanh cong!
    echo ====================================================
) else (
    echo.
    echo [ERROR] Build that bai! Vui long dam bao Docker Desktop dang bat.
)

pause
