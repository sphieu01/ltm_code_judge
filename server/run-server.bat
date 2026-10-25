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
SET DB_URL=jdbc:postgresql://ep-dry-rice-b32otp6l-pooler.c-4.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
SET DB_USER=neondb_owner
SET DB_PASSWORD=npg_GAm7Pd6hCFMt
REM ─────────────────────────────────────────

REM Cấu hình server (tùy chỉnh nếu cần)
SET SERVER_PORT=9999
SET JUDGE_WORKER_COUNT=4

REM Tự động phát hiện Java
WHERE java >nul 2>&1
IF %ERRORLEVEL% EQU 0 (
    SET "JAVA_CMD=java"
) ELSE IF EXIST "C:\Users\Admin\.jdks\openjdk-25.0.2\bin\java.exe" (
    SET "JAVA_CMD=C:\Users\Admin\.jdks\openjdk-25.0.2\bin\java.exe"
) ELSE IF DEFINED JAVA_HOME (
    SET "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
) ELSE (
    SET "JAVA_CMD=java"
)

REM Tự động phát hiện Maven
WHERE mvn >nul 2>&1
IF %ERRORLEVEL% EQU 0 (
    SET "MVN_CMD=mvn"
) ELSE IF EXIST "%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.12-bin\5nmfsn99br87k5d4ajlekdq10k\apache-maven-3.9.12\bin\mvn.cmd" (
    SET "MVN_CMD=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.12-bin\5nmfsn99br87k5d4ajlekdq10k\apache-maven-3.9.12\bin\mvn.cmd"
) ELSE IF EXIST "%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd" (
    SET "MVN_CMD=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd"
) ELSE IF EXIST "%USERPROFILE%\.antigravity-ide\extensions\oracle.oracle-java-27.0.0-universal\nbcode\java\maven\bin\mvn.cmd" (
    SET "MVN_CMD=%USERPROFILE%\.antigravity-ide\extensions\oracle.oracle-java-27.0.0-universal\nbcode\java\maven\bin\mvn.cmd"
) ELSE IF EXIST "C:\Users\ADMIN\maven\apache-maven-3.9.6\bin\mvn.cmd" (
    SET "MVN_CMD=C:\Users\ADMIN\maven\apache-maven-3.9.6\bin\mvn.cmd"
) ELSE (
    SET "MVN_CMD=mvn"
)

echo.
echo ====================================================
echo   Code Grading Server - BTL Lap Trinh Mang
echo ====================================================
echo.
echo DB URL: %DB_URL%
echo Server Port: %SERVER_PORT%
echo Judge Workers: %JUDGE_WORKER_COUNT%
echo.

REM Kiem tra va kill process cu dang giu port 9999
FOR /F "tokens=5" %%P IN ('netstat -ano ^| findstr ":9999 " ^| findstr "LISTENING"') DO (
    echo [INFO] Port 9999 dang bi giu boi PID %%P, tu dong kill...
    taskkill /PID %%P /F >nul 2>&1
    timeout /t 1 /nobreak >nul
)

REM Build fat JAR de luon cap nhat code moi nhat
echo [BUILD] Building fat JAR...
call "%MVN_CMD%" clean package -DskipTests -q
IF ERRORLEVEL 1 (
    echo [ERROR] Build failed!
    pause
    exit /b 1
)
echo [BUILD] Done!
echo.

echo [SERVER] Starting server on port %SERVER_PORT%...
echo Press Ctrl+C to stop.
echo.

"%JAVA_CMD%" -jar target\code-grading-server-1.0.0.jar

pause
