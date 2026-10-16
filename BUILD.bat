@echo off
setlocal
rem === RealWeather one-click build. Run by double click. ===

rem 1) ask for administrator rights (needed to add Defender exclusions)
net session >nul 2>&1
if not "%errorlevel%"=="0" (
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)

rem 2) find JDK 21
set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.11"
if not exist "%JAVA_HOME%\bin\java.exe" (
  for /d %%D in ("C:\Program Files\Java\jdk-21*") do set "JAVA_HOME=%%D"
)
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo JDK 21 not found in C:\Program Files\Java. Install it and run again.
  pause
  exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo JAVA_HOME=%JAVA_HOME%

rem 3) gradle cache in a path without spaces
set "GRADLE_USER_HOME=C:\gradle-home"
if not exist "%GRADLE_USER_HOME%" mkdir "%GRADLE_USER_HOME%"

cd /d "%~dp0"

rem 4) Defender exclusions (fixes CreateProcess error=5)
powershell -NoProfile -Command "Add-MpPreference -ExclusionPath '%~dp0','C:\gradle-home','%JAVA_HOME%'" >nul 2>&1

rem 5) Gradle wrapper: copy from the old project folder if missing
if not exist gradlew.bat (
  if exist "%USERPROFILE%\Desktop\RealWeather\gradlew.bat" (
    copy /y "%USERPROFILE%\Desktop\RealWeather\gradlew.bat" . >nul
    copy /y "%USERPROFILE%\Desktop\RealWeather\gradlew" . >nul
    xcopy /e /i /y "%USERPROFILE%\Desktop\RealWeather\gradle" gradle >nul
  )
)
if not exist gradlew.bat (
  echo gradlew.bat is missing. Copy gradlew, gradlew.bat and the gradle folder from the official NeoForge 1.21.1 MDK here.
  pause
  exit /b 1
)

call gradlew.bat --stop
if exist build rmdir /s /q build
if exist .gradle rmdir /s /q .gradle

call gradlew.bat clean build
echo.
if exist build\libs (
  echo ==== DONE. JAR files: ====
  dir /b build\libs
  start "" build\libs
) else (
  echo Build failed. Send the text above to Claude.
)
pause
