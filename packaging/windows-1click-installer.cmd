@echo off
setlocal
title JM Passwortmanager 1.1.0 - Setup
color 0A
cls

echo.
echo    ==============================================================
echo          J M     P A S S W O R T M A N A G E R
echo            S I C H E R     -     L O K A L     -     S C H N E L L
echo    --------------------------------------------------------------
echo         Version 1.1.0        (c) JM        AES-256 verschluesselt
echo    ==============================================================
echo.
echo      [ 1 ]  Installieren  /  Aktualisieren
echo      [ 2 ]  Deinstallieren
echo      [ 3 ]  Beenden
echo.
choice /C 123 /N /M "     Deine Auswahl: "
if errorlevel 3 goto ende
if errorlevel 2 goto uninstall
goto terms

:terms
cls
echo.
echo    --------------------------------------------------------------
echo     N U T Z U N G S B E D I N G U N G E N
echo    --------------------------------------------------------------
echo.
echo     1. Die Software ist kostenlos und wird im Ist-Zustand
echo        bereitgestellt ("AS IS") ohne jegliche Garantie.
echo.
echo     2. Alle Passwoerter werden ausschliesslich lokal auf diesem
echo        Computer verschluesselt gespeichert (AES-256-GCM).
echo        Es findet KEINE Uebertragung in die Cloud statt.
echo.
echo     3. Ein starkes Master-Passwort (mind. 12 Zeichen) sowie den
echo        28-stelligen Wiederherstellungscode sicher aufbewahren.
echo        Bei Verlust beider ist kein Zugriff mehr moeglich.
echo.
echo     4. Erstelle regelmaessig Backups deiner Daten. Der Hersteller
echo        haftet nicht fuer Datenverlust oder Folgeschaeden.
echo.
echo     5. Mit der Installation akzeptierst du diese Bedingungen.
echo        Vollstaendige Details: README auf der GitHub-Seite.
echo.
echo    --------------------------------------------------------------
echo.
choice /C JN /N /M "     Bedingungen akzeptieren und fortfahren? (J/N): "
if errorlevel 2 goto abgelehnt

:install
cls
echo.
echo    [1/4] Pruefe Installationsdateien ...
if not exist "%~dp0JM-Passwortmanager-1.1.0-windows.msi" goto dateifehlt
echo          [OK] Installationspaket gefunden.
echo.
if exist "%LocalAppData%\JM Passwortmanager\JM Passwortmanager.exe" (
    echo          Info: Bestehende Installation wird aktualisiert.
) else (
    echo          Info: Frische Installation wird durchgefuehrt.
)
echo.
echo    [2/4] Beende eventuell laufende Programm-Instanzen ...
taskkill /IM "JM Passwortmanager.exe" /F >nul 2>&1
ping -n 3 127.0.0.1 >nul
echo          [OK]
echo.
echo    [3/4] Installation wird ausgefuehrt - bitte warten ...
msiexec /i "%~dp0JM-Passwortmanager-1.1.0-windows.msi" MSIRESTARTMANAGERCONTROL=ForceShutdown /passive
if errorlevel 1 goto reparatur
goto pruefen

:reparatur
echo          [..] Erneuter Versuch nach Bereinigung alter Reste ...
rmdir /s /q "%LocalAppData%\JM Passwortmanager" >nul 2>&1
msiexec /i "%~dp0JM-Passwortmanager-1.1.0-windows.msi" MSIRESTARTMANAGERCONTROL=ForceShutdown /passive
if errorlevel 1 goto fehler

:pruefen
echo          [OK] Installation abgeschlossen.
echo.
echo    [4/4] Abschlusstest ...
if not exist "%LocalAppData%\JM Passwortmanager\JM Passwortmanager.exe" goto fehler
echo          [OK] Programm bereit.
echo.
echo    ==============================================================
echo         F E R T I G   -   I N S T A L L A T I O N   E R F O L G R E I C H
echo    ==============================================================
echo.
echo      Du findest die App im Startmenue und auf dem Desktop:
echo      "JM Passwortmanager"
echo      Deine gespeicherten Passwoerter bleiben erhalten.
echo.
choice /C JN /N /M "     Programm jetzt starten? (J/N): "
if errorlevel 2 goto ende
start "" "%LocalAppData%\JM Passwortmanager\JM Passwortmanager.exe"
ping -n 4 127.0.0.1 >nul
goto ende

:uninstall
cls
echo.
echo    [1/2] Beende eventuell laufende Programm-Instanzen ...
taskkill /IM "JM Passwortmanager.exe" /F >nul 2>&1
ping -n 2 127.0.0.1 >nul
echo          [OK]
echo.
echo    [2/2] Deinstallation wird ausgefuehrt - bitte warten ...
msiexec /x "%~dp0JM-Passwortmanager-1.1.0-windows.msi" MSIRESTARTMANAGERCONTROL=ForceShutdown /passive
if errorlevel 1 goto uninstall_hinweis
echo          [OK] Deinstallation abgeschlossen.
echo.
echo      Deine Datenbank unter .jm-passwortmanager wurde NICHT geloescht.
echo.
ping -n 7 127.0.0.1 >nul
goto ende

:uninstall_hinweis
echo.
echo      Hinweis: Falls eine andere Version installiert war, deinstalliere
echo      diese bitte ueber Windows-Einstellungen - Apps.
echo.
ping -n 9 127.0.0.1 >nul
goto ende

:abgelehnt
cls
color 0C
echo.
echo    Installation abgebrochen - die Nutzungsbedingungen wurden
echo    nicht akzeptiert.
echo.
echo    Du kannst dieses Setup jederzeit erneut starten.
echo.
ping -n 7 127.0.0.1 >nul
exit /b 2

:dateifehlt
cls
color 0C
echo.
echo    FEHLER: Die Datei "JM-Passwortmanager-1.1.0-windows.msi"
echo    wurde neben diesem Programm nicht gefunden.
echo.
echo    Beide Dateien muessen im selben Ordner liegen.
echo    Bitte das komplette ZIP entpacken und erneut starten.
echo.
pause
exit /b 2

:fehler
cls
color 0C
echo.
echo    ==============================================================
echo         D I E   I N S T A L L A T I O N   W U R D E   A B G E B R O C H E N
echo    ==============================================================
echo.
echo      Bitte folgendes versuchen:
echo.
echo       1. Alle geoeffneten Programme schliessen
echo       2. Dieses Setup erneut starten
echo       3. Bleibt das Problem: PC neu starten und erneut versuchen
echo.
echo      Technische Details (falls vorhanden):
echo      C:\Users\DeinName\.jm-passwortmanager\error.log
echo.
pause
exit /b 1

:ende
color 07
exit /b 0
