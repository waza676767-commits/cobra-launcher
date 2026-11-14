; Builds AudioBridge-Setup.exe: installs Audio Bridge with Start menu + desktop icons.
[Setup]
AppName=Audio Bridge
AppVersion=1.5
AppId={{6A1F0B8E-3D4C-4E8A-9B7F-AB1D6E2C9F10}
DefaultDirName={localappdata}\Programs\Audio Bridge
DefaultGroupName=Audio Bridge
PrivilegesRequired=lowest
DisableProgramGroupPage=yes
OutputDir=..\..\out
OutputBaseFilename=AudioBridge-Setup
SetupIconFile=icon.ico
UninstallDisplayIcon={app}\AudioBridge.exe
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
CloseApplications=yes

[Tasks]
Name: "desktopicon"; Description: "Create a desktop icon"; Flags: checkedonce
Name: "autorun"; Description: "Open Audio Bridge when Windows starts"; Flags: unchecked

[Files]
Source: "..\..\out\AudioBridge.exe"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\Audio Bridge"; Filename: "{app}\AudioBridge.exe"
Name: "{userdesktop}\Audio Bridge"; Filename: "{app}\AudioBridge.exe"; Tasks: desktopicon

[Registry]
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: string; ValueName: "AudioBridge"; ValueData: """{app}\AudioBridge.exe"" --tray"; Tasks: autorun; Flags: uninsdeletevalue

[Run]
Filename: "{app}\AudioBridge.exe"; Description: "Open Audio Bridge now"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
Type: dirifempty; Name: "{app}"
