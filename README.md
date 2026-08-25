# 🔒 JM Passwortmanager

Ein moderner, komplett lokaler und sicherer Passwortmanager mit eleganter Benutzeroberfläche – geschrieben in Java mit JavaFX.

Dieses Projekt speichert **keine Daten in der Cloud**. Du hast die 100 %ige Kontrolle über deine verschlüsselte Datenbank, die vollständig offline auf deinem Rechner liegt.

---

## ✨ Features

* **🛡️ Starke Verschlüsselung:** Alle Einträge werden mit **AES-256-GCM** (authentifizierte Verschlüsselung) in einer lokalen SQLite-Datenbank abgelegt.
* **🔑 Härtbare Schlüssel-Ableitung:** Master-Passwort und Recovery-Code werden mit **PBKDF2-HMAC-SHA256 (600.000 Iterationen)** gehasht. Ältere Tresore werden beim Entsperren automatisch auf die neuen Parameter migriert.
* **🔓 Angemeldet bleiben:** Optional bleibt der Tresor über mehrere Starts entsperrt. Der Sitzungsschlüssel wird dabei per **Windows-DPAPI** an dein Benutzerkonto gebunden. Ausloggen jederzeit über den ⏻-Button.
* **⏲️ Auto-Lock:** Nach 5 Minuten Inaktivität sperrt sich der Tresor automatisch.
* **🔐 Integrierte 2FA-Codes (TOTP):** 2FA-Secret pro Eintrag hinterlegen (Base32 oder `otpauth://`-Link) – der 6-stellige Code wird live auf der Karte angezeigt, aktualisiert sich alle 30 Sekunden selbst und ist kopierbar.
* **🌐 Login-Assistent:** Öffnest du eine Website über den 🌍-Button, erkennt der Manager den Eintrag, fragt „Automatisch einloggen?" und legt Benutzername & Passwort nacheinander in die Zwischenablage.
* **🌍 Zwei Sprachen:** Deutsch & Englisch – umschaltbar über den Sprach-Button oben rechts (bzw. auf jedem Anmeldebildschirm), Auswahl wird gespeichert.
* **❓ 2FA-Hilfe integriert:** Das „?" neben dem 2FA-Feld erklärt Schritt für Schritt, wo du den Schlüssel auf der Website findest.
* **📂 Workspaces:** Trenne z. B. „Privat" und „Arbeit" sauber voneinander.
* **🎲 Passwort-Generator:** Sichere Zufallspasswörter mit einstellbarer Länge und Zeichensätzen. Eintrags-Passwörter dürfen beliebige Längen haben – die Mindestlänge gilt nur für das Master-Passwort.
* **🌓 Dark & Light Mode**, **📱 Swipe-to-Delete mit Sicherheitsabfrage**, **📋 Clipboard-Auto-Clear nach 15 s**, **⌨️ Enter-Bedienerführung** in allen Formularen.

## 🔐 Sicherheitskonzept im Überblick

| Komponente | Umsetzung |
|---|---|
| Eintragsverschlüsselung | AES-256-GCM, zufällige Nonce pro Datensatz |
| Schlüsselableitung | PBKDF2-HMAC-SHA256, 600.000 Iterationen, 256 Bit |
| Master-Passwort | Min. 12 Zeichen, Groß-/Kleinbuchstaben + Zahl |
| Recovery-Code | 28 Zeichen (~160 Bit Entropie), ersetzt schwache Legacy-Codes automatisch |
| „Angemeldet bleiben" | Windows DPAPI (an Benutzerkonto gebunden), Fallback: lokale Schlüsseldatei |
| Manipulationserkennung | GCM-Authentifizierungstag pro Datensatz |

## 🛠️ Tech-Stack

* **Sprache:** Java 17+
* **Framework:** JavaFX 17.0.10
* **Datenbank:** SQLite (über JDBC)
* **Windows-Integration:** JNA (DPAPI)
* **Build-Tool:** Maven

---

## 🚀 Selbst bauen

### Voraussetzungen
* JDK 17 oder neuer
* Maven

### Bauen & Starten
```bash
git clone https://github.com/j4yac3/Simple-Passwortmanager.git
cd Simple-Passwortmanager
mvn clean package
java -jar target/JMPasswortmanager-1.0-SNAPSHOT.jar
```

### Installationspakete erstellen (optional)
Der Build erzeugt ein Fat-JAR mit gebündelten Abhängigkeiten:

```bash
# Windows-Installer (.msi, benötigt WiX Toolset 3.11)
mvn clean package
jpackage --type msi --input target --main-jar JMPasswortmanager-1.0-SNAPSHOT.jar ^
         --main-class org.example.Launcher --name "JM Passwortmanager" ^
         --app-version 1.0 --icon icon.ico --win-shortcut --win-menu --win-per-user-install

# Linux (.deb) – auf einem Linux-System ausführen:
jpackage --type deb --input target --main-jar JMPasswortmanager-1.0-SNAPSHOT.jar \
         --main-class org.example.Launcher --name jm-passwortmanager --app-version 1.0 \
         --linux-shortcut

# macOS (.dmg) – auf einem Mac ausführen:
jpackage --type dmg --input target --main-jar JMPasswortmanager-1.0-SNAPSHOT.jar \
         --main-class org.example.Launcher --name "JM Passwortmanager" --app-version 1.0
```

> **Hinweis zu iOS/iPadOS:** Apple erlaubt keine Java-Apps auf iPhone/iPad. Diese App ist für Desktop (Windows/Linux/macOS).

## 📁 Wo liegen meine Daten?

Alles lokal unter `%USERPROFILE%\.jm-passwortmanager\` (Linux/macOS: `~/.jm-passwortmanager/`):

| Datei | Inhalt |
|---|---|
| `vault.db` | Verschlüsselte Datenbank (Einträge, Konfiguration) |
| `session.key` | Nur bei aktivem „Angemeldet bleiben": DPAPI-geschützter Sitzungsschlüssel |

## ⚠️ Ehrliche Sicherheitshinweise

* „Angemeldet bleiben" ist ein Kompromiss: Wer als dasselbe Windows-Konto angemeldet ist, kann den Tresor öffnen (wie gespeicherte Browser-Passwörter). Für maximale Sicherheit die Checkbox deaktiviert lassen.
* Die Windows-Zwischenablagen-Historie (`Win+V`) behält Kopien trotz Auto-Clear.
* Die App ist nicht signiert; SmartScreen/Gatekeeper zeigen daher beim ersten Start eine Warnung.
