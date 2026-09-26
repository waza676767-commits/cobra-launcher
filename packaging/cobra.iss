; Windows installer for Cobra Launcher (Inno Setup 6). Built by build-windows.bat / the GitHub
; workflow from the portable app in dist\Cobra Launcher. Installs for the current user only, so
; testers don't need admin rights. Works on Windows 10 and 11 (64-bit).
#define AppVersion "2.1.0"

[Setup]
AppId={{6F1D8A52-1C7E-4B4E-9F2A-C0B7A0000001}
AppName=Cobra Launcher
AppVersion={#AppVersion}
AppPublisher=Cobra
DefaultDirName={autopf}\Cobra Launcher
DefaultGroupName=Cobra Launcher
DisableProgramGroupPage=yes
OutputDir=..\dist
OutputBaseFilename=Cobra Launcher-{#AppVersion}
SetupIconFile=cobra.ico
UninstallDisplayIcon={app}\Cobra Launcher.exe
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
Source: "..\dist\Cobra Launcher\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{autoprograms}\Cobra Launcher"; Filename: "{app}\Cobra Launcher.exe"
Name: "{autodesktop}\Cobra Launcher"; Filename: "{app}\Cobra Launcher.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\Cobra Launcher.exe"; Description: "Start Cobra Launcher"; Flags: nowait postinstall skipifsilent
