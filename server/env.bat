@echo off
REM ================================================================
REM  env.bat --- Credentials THAT cua Neon (KHONG commit len Git!)
REM ================================================================

REM -- Database (Neon PostgreSQL) -----------------------------------
set DB_URL=jdbc:postgresql://ep-dry-rice-b32otp6l-pooler.c-4.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
set DB_USER=neondb_owner
set DB_PASSWORD=npg_GAm7Pd6hCFMt

REM -- Server -------------------------------------------------------
set SERVER_PORT=9999
set CONNECTION_POOL_SIZE=20
set JUDGE_WORKER_COUNT=4

REM -- JWT Secret ---------------------------------------------------
set JWT_SECRET=MyVerySecretKeyForCodeGradingSystem2024!ThisIsLong

echo [env] Environment variables da duoc load.
