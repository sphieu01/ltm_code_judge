@echo off
REM ============================================================
REM run-client.bat — Chạy Code Grading Client (JavaFX)
REM ============================================================

REM 1. Tự động phát hiện và gán JAVA_HOME
WHERE java >nul 2>&1
IF NOT DEFINED JAVA_HOME (
    IF EXIST "C:\Users\Admin\.jdks\openjdk-25.0.2" (
        SET "JAVA_HOME=C:\Users\Admin\.jdks\openjdk-25.0.2"
    ) ELSE IF EXIST "C:\Program Files\Java\jdk-21" (
        SET "JAVA_HOME=C:\Program Files\Java\jdk-21"
    ) ELSE IF EXIST "C:\Program Files\Java\jdk-17" (
        SET "JAVA_HOME=C:\Program Files\Java\jdk-17"
    )
)

IF DEFINED JAVA_HOME (
    SET "PATH=%JAVA_HOME%\bin;%PATH%"
)

REM 2. Tự động phát hiện Maven
WHERE mvn >nul 2>&1
IF %ERRORLEVEL% EQU 0 (
    SET "MVN_CMD=mvn"
) ELSE IF EXIST "C:\Users\ADMIN\maven\apache-maven-3.9.6\bin\mvn.cmd" (
    SET "MVN_CMD=C:\Users\ADMIN\maven\apache-maven-3.9.6\bin\mvn.cmd"
) ELSE IF EXIST "%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd" (
    SET "MVN_CMD=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd"
) ELSE IF EXIST "%USERPROFILE%\.antigravity-ide\extensions\oracle.oracle-java-27.0.0-universal\nbcode\java\maven\bin\mvn.cmd" (
    SET "MVN_CMD=%USERPROFILE%\.antigravity-ide\extensions\oracle.oracle-java-27.0.0-universal\nbcode\java\maven\bin\mvn.cmd"
) ELSE (
    SET "MVN_CMD=mvn"
)

echo ====================================================
echo   Code Grading Client (JavaFX)
echo ====================================================
IF DEFINED JAVA_HOME echo [INFO] JAVA_HOME: %JAVA_HOME%
echo [INFO] Maven CMD: %MVN_CMD%
echo [INFO] Starting Client UI...
echo.

set "CLIENT_JAR=target\grading-client-1.0-SNAPSHOT.jar"

REM Uu tien chay JAR da build de co the mo nhieu client cung luc.
IF EXIST "%CLIENT_JAR%" (
    echo [INFO] Starting existing Client JAR...
    start "Auto Grader Client" javaw -jar "%CLIENT_JAR%"
    exit /b 0
)

echo [INFO] Client JAR not found. Building it first...
REM Khong dung "clean": mot client dang chay co the khoa file JAR tren Windows.
call "%MVN_CMD%" package -DskipTests

IF NOT ERRORLEVEL 1 (
    start "Auto Grader Client" javaw -jar "%CLIENT_JAR%"
    exit /b 0
)

IF ERRORLEVEL 1 (
    echo.
    echo [ERROR] Ung dung Client gap loi khi khoi dong.
)

pause
