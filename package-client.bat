@echo off
REM ============================================================
REM package-client.bat — Đóng gói Client thành file ZIP gửi sinh viên
REM ============================================================

setlocal enabledelayedexpansion
cd /d "%~dp0"

echo.
echo ====================================================
echo   DONG GOI CLIENT AUTO GRADER (ZIP DISTRIBUTION)
echo ====================================================
echo.

REM 1. Phat hien Maven
WHERE mvn >nul 2>&1
IF %ERRORLEVEL% EQU 0 (
    SET "MVN_CMD=mvn"
) ELSE IF EXIST "%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.12-bin\5nmfsn99br87k5d4ajlekdq10k\apache-maven-3.9.12\bin\mvn.cmd" (
    SET "MVN_CMD=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.12-bin\5nmfsn99br87k5d4ajlekdq10k\apache-maven-3.9.12\bin\mvn.cmd"
) ELSE IF EXIST "%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd" (
    SET "MVN_CMD=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd"
) ELSE IF EXIST "C:\Users\ADMIN\maven\apache-maven-3.9.6\bin\mvn.cmd" (
    SET "MVN_CMD=C:\Users\ADMIN\maven\apache-maven-3.9.6\bin\mvn.cmd"
) ELSE (
    SET "MVN_CMD=mvn"
)

REM 2. Build Fat JAR
echo [1/4] Dang build Fat JAR cho Client...
cd client
call "%MVN_CMD%" clean package -DskipTests -q
IF ERRORLEVEL 1 (
    echo [ERROR] Build Client that bai!
    cd ..
    pause
    exit /b 1
)
cd ..

REM 3. Tao thu muc release
echo [2/4] Chuan bi file phan phoi...
IF EXIST "release\client-dist" rmdir /s /q "release\client-dist"
mkdir "release\client-dist"

copy /y "client\target\grading-client-1.0-SNAPSHOT.jar" "release\client-dist\grading-client.jar" >nul

REM 4. Tao config.properties mac dinh voi domain hoangnv.dev
IF NOT EXIST "release\client-dist\config.properties" (
    (
        echo # ============================================================
        echo # Auto Grader Client - Cau hinh ket noi Server
        echo # ============================================================
        echo server.host=0.tcp.ap.ngrok.io
        echo server.port=23480
    ) > "release\client-dist\config.properties"
)

REM 5. Tao run-client.bat trong thu muc phan phoi
(
    echo @echo off
    echo title Auto Grader Client
    echo cd /d "%%~dp0"
    echo WHERE java ^>nul 2^>^&1
    echo IF %%ERRORLEVEL%% NEQ 0 (
    echo     echo [ERROR] May cua ban chua cai dat Java 17 tro len!
    echo     echo Vui long cai dat Java de chay ung dung.
    echo     pause
    echo     exit /b 1
    echo ^)
    echo start "" javaw -jar "grading-client.jar"
) > "release\client-dist\run-client.bat"

REM 6. Dong goi file ZIP bang PowerShell
echo [3/4] Dang nen thu muc thanh file ZIP...
powershell -Command "if (Test-Path 'release\AutoGrader-Client.zip') { Remove-Item 'release\AutoGrader-Client.zip' -Force }; Compress-Archive -Path 'release\client-dist\*' -DestinationPath 'release\AutoGrader-Client.zip' -Force"

echo [4/4] Hoan tat!
echo.
echo ====================================================
echo   FILE ZIP DA SAN SANG DE GUI CHO NGUOI DUNG:
echo   release\AutoGrader-Client.zip
echo ====================================================
echo.
echo Ban co the doi server.port trong file release\client-dist\config.properties
echo (neu Playit cap port khac) roi chay lai file bat nay bat cu luc nao.
echo.
pause
