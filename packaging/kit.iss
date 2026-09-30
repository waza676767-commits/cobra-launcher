; Windows installer for Kit Launcher (Inno Setup 6). Built by build-windows.bat / the GitHub
; workflow from the portable app in dist\Kit Launcher. Installs for the current user only, so
; testers don't need admin rights. Works on Windows 10 and 11 (64-bit).
#define AppVersion "2.1.0"

[Setup]
AppId={{6F1D8A52-1C7E-4B4E-9F2A-C0B7A0000001}
AppName=Kit Launcher
AppVersion={#AppVersion}
AppPublisher=Kit
DefaultDirName={autopf}\Kit Launcher
DefaultGroupName=Kit Launcher
DisableProgramGroupPage=yes
OutputDir=..\dist
OutputBaseFilename=Kit Launcher-{#AppVersion}
SetupIconFile=kit.ico
UninstallDisplayIcon={app}\Kit Launcher.exe
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
Source: "..\dist\Kit Launcher\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{autoprograms}\Kit Launcher"; Filename: "{app}\Kit Launcher.exe"
Name: "{autodesktop}\Kit Launcher"; Filename: "{app}\Kit Launcher.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\Kit Launcher.exe"; Description: "Start Kit Launcher"; Flags: nowait postinstall skipifsilent
