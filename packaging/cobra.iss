; Windows installer for Abyss Launcher (Inno Setup 6). Built by build-windows.bat / the GitHub
; workflow from the portable app in dist\Abyss Launcher. Installs for the current user only, so
; testers don't need admin rights. Works on Windows 10 and 11 (64-bit).
#define AppVersion "2.1.0"

[Setup]
AppId={{6F1D8A52-1C7E-4B4E-9F2A-C0B7A0000001}
AppName=Abyss Launcher
AppVersion={#AppVersion}
AppPublisher=Abyss
DefaultDirName={autopf}\Abyss Launcher
DefaultGroupName=Abyss Launcher
DisableProgramGroupPage=yes
OutputDir=..\dist
OutputBaseFilename=Abyss Launcher-{#AppVersion}
SetupIconFile=cobra.ico
UninstallDisplayIcon={app}\Abyss Launcher.exe
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
Source: "..\dist\Abyss Launcher\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{autoprograms}\Abyss Launcher"; Filename: "{app}\Abyss Launcher.exe"
Name: "{autodesktop}\Abyss Launcher"; Filename: "{app}\Abyss Launcher.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\Abyss Launcher.exe"; Description: "Start Abyss Launcher"; Flags: nowait postinstall skipifsilent
