@echo off
REM ================================================================
REM  env.example.bat --- Mau cau hinh environment variables
REM  COPY file nay thanh "env.bat" roi dien thong tin Neon that
REM  KHONG commit "env.bat" len Git!
REM ================================================================

REM -- Database (Neon PostgreSQL) -----------------------------------
REM  Lay tu: console.neon.tech -> Project -> Connection Details -> JDBC
set DB_URL=postgresql://neondb_owner:your_neon_password_here@ep-dry-rice-b32otp6l-pooler.c-4.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require
set DB_USER=neondb_owner
set DB_PASSWORD=your_neon_password_here

REM -- Server -------------------------------------------------------
set SERVER_PORT=9999
set CONNECTION_POOL_SIZE=20
set JUDGE_WORKER_COUNT=4

REM -- JWT Secret (doi thanh chuoi random dai >= 32 ky tu) ----------
set JWT_SECRET=CHANGE_ME_TO_RANDOM_SECRET_AT_LEAST_32_CHARS

echo [env] Environment variables da duoc load.

