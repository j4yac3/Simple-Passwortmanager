; JM Password Manager - Setup Wizard Script
; Inno Setup 6

#define AppName "JM Password Manager"
#define AppVersion "1.1.0"
#define AppPublisher "JM"
#define AppExe "JM Password Manager.exe"

[Setup]
AppId={{8E4F2C1A-77B3-4D9E-9A51-C0FFEE110110}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
DefaultDirName={localappdata}\{#AppName}
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
WizardStyle=modern
SetupIconFile=icon.ico
UninstallDisplayIcon={app}\{#AppExe}
OutputDir=.
OutputBaseFilename=JM-Password-Manager-1.1.0-setup
Compression=lzma2/max
SolidCompression=yes
InfoBeforeFile=INFO-BEFORE.txt
LicenseFile=EULA.txt

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a &desktop shortcut"; GroupDescription: "Additional shortcuts:"

[Files]
Source: "appimage\JM Password Manager\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#AppName}"; Filename: "{app}\{#AppExe}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExe}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExe}"; Description: "&Launch JM Password Manager"; Flags: nowait postinstall skipifsilent

[Messages]
WelcomeLabel2=This will install [name/ver] on your computer.%n%nA fast, beautiful and 100% local password manager.%n%nAll passwords are stored encrypted (AES-256-GCM) on this computer only - no cloud, no accounts, no tracking.%n%nIt is recommended that you close all other applications before continuing.
FinishedLabel=Thank you for using JM Password Manager!%n%nYour vault was installed successfully. You will find the app in the Start menu and on your desktop.%n%nIMPORTANT: On first start, write down your 28-character recovery code - it is the only way to reset a forgotten master password.
SelectDirDesc=Where should [name] be installed?
SelectDirLabel3=Setup will install [name] into the following folder.
SelectDirBrowseLabel=To continue, click Next. If you would like a different folder, click Browse.
