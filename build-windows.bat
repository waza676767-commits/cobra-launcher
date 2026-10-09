@echo off
rem Builds Life Launcher for Windows 10/11:
rem   dist\Life Launcher\Life Launcher.exe   (portable app with its own Java, no install needed)
rem   dist\Life Launcher-2.1.0.exe             (installer; only if Inno Setup 6 is installed)
rem Needs a JDK 21 (e.g. Temurin 21) on PATH. Run from this folder: build-windows.bat
setlocal
cd /d "%~dp0"

if not exist build mkdir build
if exist client\build\libs del /q client\build\libs\life-client-*.jar 2>nul
rem One Life Client per Minecraft version in client\versions.txt. The first must build; the
rem others are skipped if they fail (those versions then run without Life Client).
set FIRST=1
set FAILED=
for /f "usebackq eol=# tokens=*" %%V in ("client\versions.txt") do call :buildclient %%V
if defined FAILED exit /b 1
del /q client\build\libs\*-sources.jar 2>nul

echo ==^> Building the launcher
call gradlew.bat --no-daemon :launcher:clean :launcher:jar
if errorlevel 1 exit /b 1

if exist dist rmdir /s /q dist
mkdir dist
mkdir build\win-input 2>nul
copy /y launcher\build\libs\life-launcher.jar build\win-input\ >nul

echo ==^> Packing Life Launcher.exe (portable)
jpackage --type app-image --name "Life Launcher" --app-version 2.1.0 ^
  --input build\win-input --main-jar life-launcher.jar --main-class dev.life.launcher.LifeLauncher ^
  --icon packaging\life.ico --dest dist ^
  --java-options "-Xms96m -Xmx640m -XX:+UseG1GC -XX:MaxGCPauseMillis=8 -Dswing.aatext=true -Dsun.java2d.uiScale.enabled=true -Dsun.java2d.d3d=false -Dsun.java2d.noddraw=true"
if errorlevel 1 exit /b 1

echo ==^> Packing the installer (Inno Setup 6; skipped if it isn't installed)
set "ISCC=%ProgramFiles(x86)%\Inno Setup 6\ISCC.exe"
if not exist "%ISCC%" set "ISCC=%ProgramFiles%\Inno Setup 6\ISCC.exe"
if exist "%ISCC%" (
    "%ISCC%" /Q packaging\life.iss
    if errorlevel 1 echo Installer failed. The portable "dist\Life Launcher" folder works on its own.
) else (
    echo No Inno Setup: installer skipped. The portable "dist\Life Launcher" folder works on its own.
)

echo.
echo ==^> Done: dist\Life Launcher\Life Launcher.exe
endlocal

goto :eof

:buildclient
echo ==^> Building Life Client (Minecraft %1, Fabric)
pushd client
call gradlew.bat --no-daemon build -Pmc=%1 > ..\build\client-build-%1.log 2>&1
set RESULT=%errorlevel%
popd
if not "%RESULT%"=="0" (
    del /q client\build\libs\life-client-%1-*.jar 2>nul
    if "%FIRST%"=="1" (
        type build\client-build-%1.log
        echo *** Life Client for %1 failed to build. No Windows package was produced.
        set FAILED=1
        set FIRST=0
        exit /b 1
    )
    echo *** Life Client for %1 did not build: that version runs without Life Client.
)
set FIRST=0
exit /b 0
