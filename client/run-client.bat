@echo off
REM ============================================================
REM run-client.bat — Chay Code Grading Client (JavaFX)
REM ============================================================

REM Kiem tra Maven
WHERE mvn >nul 2>&1
IF %ERRORLEVEL% EQU 0 (
    SET "MVN_CMD=mvn"
) ELSE IF EXIST "%USERPROFILE%\.antigravity-ide\extensions\oracle.oracle-java-27.0.0-universal\nbcode\java\maven\bin\mvn.cmd" (
    SET "MVN_CMD=%USERPROFILE%\.antigravity-ide\extensions\oracle.oracle-java-27.0.0-universal\nbcode\java\maven\bin\mvn.cmd"
) ELSE (
    SET "MVN_CMD=mvn"
)

echo ====================================================
echo   Code Grading Client (JavaFX)
echo ====================================================
echo Starting Client UI...
echo.

call "%MVN_CMD%" javafx:run
