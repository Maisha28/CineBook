@echo off
echo ============================================================
echo   Starting CineBook Java Bridge (:8080)
echo ============================================================
cd /d "%~dp0Backend\bridge"
if not exist "out" (
    echo Building bridge classes first...
    call build.bat
)
call run.bat
