@echo off
REM ============================================================
REM  CineBook Bridge Build Script
REM  Run from: Frontend\bridge\
REM ============================================================

set SRC=src
set OUT=out
if exist "..\lib\postgresql-42.7.4.jar" (
    set LIB=..\lib\postgresql-42.7.4.jar
) else (
    set LIB=..\..\Backend\lib\postgresql-42.7.4.jar
)

echo [1/3] Cleaning output directory...
if exist %OUT% rmdir /s /q %OUT%
mkdir %OUT%

echo [2/3] Compiling bridge sources...
javac -cp "%LIB%" -d %OUT% %SRC%\*.java
if %ERRORLEVEL% neq 0 (
    echo BUILD FAILED
    exit /b 1
)

echo [3/3] Build successful.
echo.
echo To start the bridge server run:
echo   run.bat
echo.
echo The UI will be available at http://localhost:8080
