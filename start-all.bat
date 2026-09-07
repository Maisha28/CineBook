@echo off
echo ============================================================
echo   Starting CineBook: Backend Bridge + React Frontend
echo ============================================================
echo.
echo [1/2] Starting Java Bridge Server on port 8080...
start "CineBook Java Bridge (Port 8080)" cmd /k "cd /d "%~dp0Backend\bridge" && call run.bat"
timeout /t 2 /nobreak >nul

echo [2/2] Starting React Frontend on port 3000...
start "CineBook React UI (Port 3000)" cmd /k "cd /d "%~dp0Frontend" && npm run dev"

echo.
echo ============================================================
echo   CineBook is now running!
echo   - Consumer Ticketing UI:  http://localhost:3000
echo   - Systems Lab Workbench:  http://localhost:3000/lab
echo   - Java Bridge Server:     http://localhost:8080
echo ============================================================
echo.
