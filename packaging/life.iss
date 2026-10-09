; Windows installer for Life Launcher (Inno Setup 6). Built by build-windows.bat / the GitHub
; workflow from the portable app in dist\Life Launcher. Installs for the current user only, so
; testers don't need admin rights. Works on Windows 10 and 11 (64-bit).
#define AppVersion "2.1.0"

[Setup]
AppId={{6F1D8A52-1C7E-4B4E-9F2A-C0B7A0000001}
AppName=Life Launcher
AppVersion={#AppVersion}
AppPublisher=Life
DefaultDirName={autopf}\Life Launcher
DefaultGroupName=Life Launcher
DisableProgramGroupPage=yes
; always the Life Launcher folder (not an old install's folder name)
UsePreviousAppDir=no
OutputDir=..\dist
OutputBaseFilename=Life Launcher-{#AppVersion}
SetupIconFile=life.ico
UninstallDisplayIcon={app}\Life Launcher.exe
Compression=lzma2
SolidCompression=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=lowest
WizardStyle=modern
MinVersion=10.0

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Shortcuts:"

[Files]
Source: "..\dist\Life Launcher\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[InstallDelete]
; the launcher's old installs (it had other names before Life): their folders and shortcuts go
Type: filesandordirs; Name: "{autopf}\Abyss Launcher"
Type: filesandordirs; Name: "{autopf}\Cobra Launcher"
Type: files; Name: "{autoprograms}\Abyss Launcher.lnk"
Type: files; Name: "{autodesktop}\Abyss Launcher.lnk"
Type: files; Name: "{autoprograms}\Cobra Launcher.lnk"
Type: files; Name: "{autodesktop}\Cobra Launcher.lnk"

[Icons]
Name: "{autoprograms}\Life Launcher"; Filename: "{app}\Life Launcher.exe"
Name: "{autodesktop}\Life Launcher"; Filename: "{app}\Life Launcher.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\Life Launcher.exe"; Description: "Start Life Launcher"; Flags: nowait postinstall skipifsilent
