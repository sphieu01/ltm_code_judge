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

echo [INFO] Cleaning and compiling client classes...
call "%MVN_CMD%" clean compile javafx:run

IF ERRORLEVEL 1 (
    echo.
    echo [ERROR] Ung dung Client gap loi khi khoi dong.
)

pause
