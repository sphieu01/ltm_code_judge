@echo off
REM ============================================================
REM run-client.bat - Chay Code Grading Client (JavaFX)
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
    SET "JAVAW_CMD=%JAVA_HOME%\bin\javaw.exe"
) ELSE (
    SET "JAVAW_CMD=javaw"
)

REM 2. Tự động phát hiện Maven
WHERE mvn >nul 2>&1
IF %ERRORLEVEL% EQU 0 (
    SET "MVN_CMD=mvn"
) ELSE IF EXIST "%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.12-bin\5nmfsn99br87k5d4ajlekdq10k\apache-maven-3.9.12\bin\mvn.cmd" (
    SET "MVN_CMD=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.12-bin\5nmfsn99br87k5d4ajlekdq10k\apache-maven-3.9.12\bin\mvn.cmd"
) ELSE IF EXIST "C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd" (
    SET "MVN_CMD=C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd"
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

set "CLIENT_DIR=%~dp0"
set "CLIENT_JAR=%CLIENT_DIR%target\grading-client-1.0-SNAPSHOT.jar"

echo [INFO] Dang kiem tra va dong goi code moi nhat...
call "%MVN_CMD%" package -DskipTests

IF EXIST "%CLIENT_JAR%" (
    echo [INFO] Dang khoi chay Client UI...
    start "Auto Grader Client" /D "%CLIENT_DIR%" "%JAVAW_CMD%" -jar "%CLIENT_JAR%"
    echo [INFO] Da mo Client thanh cong.
    exit /b 0
) ELSE (
    echo.
    echo [ERROR] Khong the build hoac khong tim thay file JAR Client.
    pause
    exit /b 1
)
