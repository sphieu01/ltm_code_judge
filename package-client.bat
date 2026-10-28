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
echo [2/4] Chuan bi file phan phoi va cau hinh ket noi...
IF NOT EXIST "release" mkdir "release"

REM Tu dong lay Host va Port tu Ngrok dang chay (neu co)
SET "DEFAULT_HOST=0.tcp.ap.ngrok.io"
SET "DEFAULT_PORT=13988"

powershell -NoProfile -Command "$r = (Invoke-RestMethod 'http://127.0.0.1:4040/api/tunnels' -ErrorAction SilentlyContinue); if ($r) { foreach ($t in $r.tunnels) { if ($t.proto -eq 'tcp') { $u = [Uri]$t.public_url; [IO.File]::WriteAllText('release\.endpoint', ($u.Host + ' ' + $u.Port)); break } } }" >nul 2>&1
if exist "release\.endpoint" (
    for /f "tokens=1,2" %%a in (release\.endpoint) do (
        SET "DEFAULT_HOST=%%a"
        SET "DEFAULT_PORT=%%b"
    )
    del /f /q "release\.endpoint" >nul 2>&1
)

echo     -^> Server Host: !DEFAULT_HOST!
echo     -^> Server Port: !DEFAULT_PORT!

IF EXIST "release\client-dist" rmdir /s /q "release\client-dist"
mkdir "release\client-dist"

copy /y "client\target\grading-client-1.0-SNAPSHOT.jar" "release\client-dist\grading-client.jar" >nul

REM 4. Tao config.properties
(
    echo # ============================================================
    echo # Auto Grader Client - Cau hinh ket noi Server
    echo # ============================================================
    echo server.host=!DEFAULT_HOST!
    echo server.port=!DEFAULT_PORT!
) > "release\client-dist\config.properties"

REM 5. Tao HUONG_DAN.txt
(
    echo ============================================================
    echo   HUONG DAN SU DUNG - AUTO GRADER CLIENT ^(LAP TRINH MANG^)
    echo ============================================================
    echo.
    echo 1. YEU CAU HE THONG:
    echo    - May tinh da cai dat Java 17 tro len ^(JDK 17, JDK 21, JDK 25...^).
    echo    - Neu chua co Java, tai mien phi tai: https://adoptium.net/
    echo.
    echo 2. KHOI CHAY UNG DUNG:
    echo    - Giai nen toan bo thu muc trong file ZIP nay.
    echo    - Click dup vao file "run-client.bat" de mo giao dien Client.
    echo    - Ung dung da duoc cau hinh san ket noi toi Server qua Ngrok:
    echo        Host: !DEFAULT_HOST!
    echo        Port: !DEFAULT_PORT!
    echo    ^(Neu can doi Server, ban co the chinh sua trong file "config.properties"^).
    echo.
    echo 3. TAI KHOAN TRAI NGHIEM:
    echo    - Tai khoan Giao vien : teacher01   ^| Mat khau: 123456
    echo    - Tai khoan Sinh vien : phamvankien ^| Mat khau: 123456
    echo                            B23DCCN001  ^| Mat khau: 123456
    echo.
    echo 4. CAC TINH NANG CHINH:
    echo    - Lam bai tap thuat toan ^(C, C++, Java, Python^).
    echo    - Lam bai tap Java OOP: Nop file .zip chua cac class Java, he thong cham 2 giai doan ^(Reflection + Sandbox^).
    echo    - Xem ma nguon da nop theo dang da Tab va tai file ZIP ve may.
    echo    - Bang xep hang va lich su nop bai cap nhat theo thoi gian thuc.
    echo ============================================================
) > "release\client-dist\HUONG_DAN.txt"

REM 6. Tao run-client.bat trong thu muc phan phoi
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

REM 7. Dong goi file ZIP bang PowerShell
echo [3/4] Dang nen thu muc thanh file ZIP...
powershell -Command "if (Test-Path 'release\AutoGrader-Client.zip') { Remove-Item 'release\AutoGrader-Client.zip' -Force }; Compress-Archive -Path 'release\client-dist\*' -DestinationPath 'release\AutoGrader-Client.zip' -Force"

echo [4/4] Hoan tat!
echo.
echo ====================================================
echo   FILE ZIP DA SAN SANG DE GUI CHO NGUOI DUNG:
echo   release\AutoGrader-Client.zip
echo ====================================================
echo.
echo Endpoint Ngrok hien tai: !DEFAULT_HOST!:!DEFAULT_PORT!
echo.
pause
