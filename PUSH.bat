@echo off
setlocal
cd /d "%~dp0"
where git >nul 2>&1
if not "%errorlevel%"=="0" (
  echo Git is not installed. Install it from https://git-scm.com/download/win and run again.
  pause
  exit /b 1
)
echo Create an EMPTY repository on github.com (button New), then copy its URL.
echo Example: https://github.com/yourname/realweather.git
set /p URL=Paste repository URL and press Enter: 
if not exist .git git init
git add .
git commit -m "RealWeather"
git branch -M main
git remote remove origin >nul 2>&1
git remote add origin %URL%
git push -u origin main
echo.
echo If there was no error: open your repo on github.com, tab Actions, wait for the green check,
echo then download the artifact realweather-jar. Unzip it - this is your mod JAR for the mods folder.
pause
