@echo off
rem Builds Cobra Launcher for Windows 10/11:
rem   dist\Cobra Launcher\Cobra Launcher.exe   (portable app with its own Java, no install needed)
rem   dist\Cobra Launcher-2.1.0.exe             (installer; only if Inno Setup 6 is installed)
rem Needs a JDK 21 (e.g. Temurin 21) on PATH. Run from this folder: build-windows.bat
setlocal
cd /d "%~dp0"

echo ==^> Building Cobra Client (Minecraft 1.21.11, Fabric)
if not exist build mkdir build
pushd client
call gradlew.bat --no-daemon clean build > ..\build\client-build.log 2>&1
if errorlevel 1 (
    echo.
    type ..\build\client-build.log
    echo *** Cobra Client build failed. No Windows package was produced.
    popd
    exit /b 1
)
popd

echo ==^> Building the launcher
call gradlew.bat --no-daemon :launcher:clean :launcher:jar
if errorlevel 1 exit /b 1

if exist dist rmdir /s /q dist
mkdir dist
mkdir build\win-input 2>nul
copy /y launcher\build\libs\cobra-launcher.jar build\win-input\ >nul

echo ==^> Packing Cobra Launcher.exe (portable)
jpackage --type app-image --name "Cobra Launcher" --app-version 2.1.0 ^
  --input build\win-input --main-jar cobra-launcher.jar --main-class dev.cobra.launcher.CobraLauncher ^
  --icon packaging\cobra.ico --dest dist ^
  --java-options "-Xms96m -Xmx640m -XX:+UseG1GC -XX:MaxGCPauseMillis=8 -Dswing.aatext=true -Dsun.java2d.uiScale.enabled=true -Dsun.java2d.d3d=false -Dsun.java2d.noddraw=true"
if errorlevel 1 exit /b 1

echo ==^> Packing the installer (Inno Setup 6; skipped if it isn't installed)
set "ISCC=%ProgramFiles(x86)%\Inno Setup 6\ISCC.exe"
if not exist "%ISCC%" set "ISCC=%ProgramFiles%\Inno Setup 6\ISCC.exe"
if exist "%ISCC%" (
    "%ISCC%" /Q packaging\cobra.iss
    if errorlevel 1 echo Installer failed. The portable "dist\Cobra Launcher" folder works on its own.
) else (
    echo No Inno Setup: installer skipped. The portable "dist\Cobra Launcher" folder works on its own.
)

echo.
echo ==^> Done: dist\Cobra Launcher\Cobra Launcher.exe
endlocal
