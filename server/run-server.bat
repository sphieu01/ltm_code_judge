@echo off
REM ============================================================
REM run-server.bat — Chạy Code Grading Server
REM ============================================================
REM
REM Cách dùng:
REM   1. Điền thông tin Neon.db vào các dòng SET bên dưới
REM   2. Double-click file này để chạy
REM
REM ============================================================

REM ─── ĐIỀN THÔNG TIN NEON.DB VÀO ĐÂY ───
SET DB_URL=jdbc:postgresql://YOUR_NEON_HOST.neon.tech/YOUR_DB_NAME?sslmode=require
SET DB_USER=YOUR_NEON_USERNAME
SET DB_PASSWORD=YOUR_NEON_PASSWORD
REM ─────────────────────────────────────────

REM Cấu hình server (tùy chỉnh nếu cần)
SET SERVER_PORT=9999
SET JUDGE_WORKER_COUNT=4

REM JAVA_HOME — sửa nếu JDK của bạn ở chỗ khác
SET JAVA_HOME=C:\Program Files\Java\jdk-23

echo.
echo ====================================================
echo   Code Grading Server ^| BTL Lap Trinh Mang
echo ====================================================
echo.
echo DB URL: %DB_URL%
echo Server Port: %SERVER_PORT%
echo Judge Workers: %JUDGE_WORKER_COUNT%
echo.

REM Build fat JAR nếu chưa có
IF NOT EXIST "target\code-grading-server-1.0.0.jar" (
    echo [BUILD] Building fat JAR...
    call "C:\Users\ADMIN\maven\apache-maven-3.9.6\bin\mvn.cmd" clean package -DskipTests -q
    IF ERRORLEVEL 1 (
        echo [ERROR] Build failed!
        pause
        exit /b 1
    )
    echo [BUILD] Done!
    echo.
)

echo [SERVER] Starting server on port %SERVER_PORT%...
echo Press Ctrl+C to stop.
echo.

"%JAVA_HOME%\bin\java.exe" -jar target\code-grading-server-1.0.0.jar

pause
