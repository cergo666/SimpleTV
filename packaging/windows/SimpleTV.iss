; Inno Setup Script for SimpleTV Desktop (Windows x64)
; build-windows-packages.ps1 passes the real version from version.properties.
#ifndef AppVersion
  #define AppVersion "1.1.0"
#endif

#ifndef SourceDir
  #define SourceDir "..\..\desktop\build\windows\x64\runner\Release"
#endif

#ifndef OutputDir
  #define OutputDir "..\..\out"
#endif

#ifndef OutputBaseFilename
  #define OutputBaseFilename "SimpleTV-" + AppVersion + "-Setup"
#endif

[Setup]
AppId={{9F57D58C-5D6F-4B81-BF12-B6A082CA1759}
AppName=SimpleTV
AppVersion={#AppVersion}
AppVerName=SimpleTV {#AppVersion}
AppPublisher=Mediacast
AppPublisherURL=https://github.com/cergo666/SimpleTV
AppSupportURL=https://github.com/cergo666/SimpleTV/issues
AppUpdatesURL=https://github.com/cergo666/SimpleTV/releases
DefaultDirName={autopf}\SimpleTV
DefaultGroupName=SimpleTV
DisableProgramGroupPage=auto
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
LicenseFile=
OutputDir={#OutputDir}
OutputBaseFilename={#OutputBaseFilename}
SetupIconFile=..\icon.ico
UninstallDisplayIcon={app}\SimpleTV.exe
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
PrivilegesRequiredOverridesAllowed=commandline dialog

[Languages]
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[CustomMessages]
russian.AssociatePlaylists=Ассоциировать файлы плейлистов (.m3u, .m3u8) с SimpleTV
english.AssociatePlaylists=Associate playlist files (.m3u, .m3u8) with SimpleTV
russian.FileAssociations=Ассоциации файлов:
english.FileAssociations=File associations:
russian.PlaylistFileType=Плейлист SimpleTV
english.PlaylistFileType=SimpleTV playlist

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"
Name: "associate_m3u"; Description: "{cm:AssociatePlaylists}"; GroupDescription: "{cm:FileAssociations}"

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\SimpleTV"; Filename: "{app}\SimpleTV.exe"; IconFilename: "{app}\SimpleTV.exe"
Name: "{group}\{cm:UninstallProgram,SimpleTV}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\SimpleTV"; Filename: "{app}\SimpleTV.exe"; Tasks: desktopicon

[Registry]
Root: HKA; Subkey: "Software\Classes\.m3u"; ValueType: string; ValueName: ""; ValueData: "SimpleTV.Playlist"; Flags: uninsdeletevalue; Tasks: associate_m3u
Root: HKA; Subkey: "Software\Classes\.m3u8"; ValueType: string; ValueName: ""; ValueData: "SimpleTV.Playlist"; Flags: uninsdeletevalue; Tasks: associate_m3u
Root: HKA; Subkey: "Software\Classes\SimpleTV.Playlist"; ValueType: string; ValueName: ""; ValueData: "{cm:PlaylistFileType}"; Flags: uninsdeletekey; Tasks: associate_m3u
Root: HKA; Subkey: "Software\Classes\SimpleTV.Playlist\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\SimpleTV.exe,0"; Tasks: associate_m3u
Root: HKA; Subkey: "Software\Classes\SimpleTV.Playlist\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\SimpleTV.exe"" ""%1"""; Tasks: associate_m3u

[Run]
Filename: "{app}\SimpleTV.exe"; Description: "{cm:LaunchProgram,SimpleTV}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
Type: filesandordirs; Name: "{app}\logs"
Type: filesandordirs; Name: "{app}\cache"
