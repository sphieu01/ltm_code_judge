@echo off
REM ================================================================
REM  env.example.bat --- Mau cau hinh environment variables
REM  COPY file nay thanh "env.bat" roi dien thong tin Neon that
REM  KHONG commit "env.bat" len Git!
REM ================================================================

REM -- Database (Neon PostgreSQL) -----------------------------------
REM  Lay tu: console.neon.tech -> Project -> Connection Details -> JDBC
set DB_URL=jdbc:postgresql://ep-XXXXXXXXXXXX.REGION.aws.neon.tech/DBNAME?sslmode=require
set DB_USER=NEON_USERNAME
set DB_PASSWORD=NEON_PASSWORD

REM -- Server -------------------------------------------------------
set SERVER_PORT=9999
set CONNECTION_POOL_SIZE=20
set JUDGE_WORKER_COUNT=4

REM -- JWT Secret (doi thanh chuoi random dai >= 32 ky tu) ----------
set JWT_SECRET=CHANGE_ME_TO_RANDOM_SECRET_AT_LEAST_32_CHARS

echo [env] Environment variables da duoc load.
