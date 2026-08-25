# 🔒 JM Password Manager

A modern, fully local and secure password manager with an elegant user interface — written in Java with JavaFX.

This project stores **no data in the cloud**. You have 100 % control over your encrypted database, which resides entirely offline on your machine.

---

## ✨ Features

* **🛡️ Strong Encryption:** All entries are stored with **AES-256-GCM** (authenticated encryption) in a local SQLite database.
* **🔑 Hardened Key Derivation:** Master password and recovery code are hashed with **PBKDF2-HMAC-SHA256 (600,000 iterations)**. Older vaults are automatically migrated to the new parameters on unlock.
* **🔓 Stay Logged In:** Optionally keeps the vault unlocked across restarts. The session key is bound to your user account via **Windows DPAPI**. Log out any time with the ⏻ button.
* **⏲️ Auto-Lock:** The vault automatically locks after 5 minutes of inactivity.
* **🔐 Built-in 2FA Codes (TOTP):** Store a 2FA secret per entry (Base32 or `otpauth://` link) — the 6-digit code is displayed live on the card, refreshes every 30 seconds, and is copyable.
* **🌐 Login Assistant:** Opening a website via the 🌍 button detects the entry, asks "Auto-login?" and places username & password into the clipboard one after another.
* **🌍 Two Languages:** German & English — switchable via the language button (top-right corner on every screen), selection is persisted.
* **❓ Integrated 2FA Help:** The "?" next to the 2FA field explains step by step where to find the secret on a website.
* **📂 Workspaces:** Cleanly separate e.g. "Personal" and "Work" entries.
* **🎲 Password Generator:** Secure random passwords with configurable length and character sets.
* **🌓 Dark & Light Mode**, **📱 Swipe-to-Delete with confirmation**, **📋 Clipboard auto-clear after 15 s**, **⌨️ Enter key navigation** in all forms.

## 🔐 Security Overview

| Component | Implementation |
|---|---|
| Entry encryption | AES-256-GCM, random nonce per record |
| Key derivation | PBKDF2-HMAC-SHA256, 600,000 iterations, 256-bit |
| Master password | Min. 12 characters, upper/lowercase + digit |
| Recovery code | 28 characters (~160-bit entropy), weak legacy codes are auto-replaced |
| "Stay logged in" | Windows DPAPI (bound to user account), fallback: local key file |
| Tamper detection | GCM authentication tag per record |

## 🛠️ Tech Stack

* **Language:** Java 17+
* **Framework:** JavaFX 17.0.10
* **Database:** SQLite (via JDBC)
* **Windows Integration:** JNA (DPAPI)
* **Build Tool:** Maven

---

## 🚀 Build from Source

### Prerequisites
* JDK 17 or newer
* Maven

### Build & Run
```bash
git clone https://github.com/j4yac3/Simple-Passwortmanager.git
cd Simple-Passwortmanager
mvn clean package
java -jar target/JMPasswortmanager-1.0-SNAPSHOT.jar
```

### Create Installer Packages (optional)
The build produces a fat JAR with bundled dependencies:

```bash
# Windows Installer (.msi, requires WiX Toolset 3.11)
mvn clean package
jpackage --type msi --input target --main-jar JMPasswortmanager-1.0-SNAPSHOT.jar ^
         --main-class org.example.Launcher --name "JM Passwortmanager" ^
         --app-version 1.0 --icon icon.ico --win-shortcut --win-menu --win-per-user-install

# Linux (.deb) — run on a Linux system:
jpackage --type deb --input target --main-jar JMPasswortmanager-1.0-SNAPSHOT.jar \
         --main-class org.example.Launcher --name jm-passwortmanager --app-version 1.0 \
         --linux-shortcut

# macOS (.dmg) — run on a Mac:
jpackage --type dmg --input target --main-jar JMPasswortmanager-1.0-SNAPSHOT.jar \
         --main-class org.example.Launcher --name "JM Passwortmanager" --app-version 1.0
```

> **Note on iOS/iPadOS:** Apple does not allow Java apps on iPhone/iPad. This app is desktop-only (Windows/Linux/macOS).

## 📁 Where Is My Data?

Everything is stored locally at `%USERPROFILE%\.jm-passwortmanager\` (Linux/macOS: `~/.jm-passwortmanager/`):

| File | Contents |
|---|---|
| `vault.db` | Encrypted database (entries, configuration) |
| `session.key` | Only when "Stay logged in" is active: DPAPI-protected session key |

---

## 🆕 What's New in V1.1.0

### UI Improvements
* **Language button repositioned:** On the login and all auth screens, the language toggle is now pinned to the **top-right corner** for a cleaner, more accessible layout.
* **Custom scrollbar styling:** The default system scrollbar has been replaced with a **slim, modern scrollbar** that matches the app's dark and light themes — thin track, rounded thumb, no arrow buttons.
* **Theme-aware scrollbars:** Scrollbar appearance automatically adapts when switching between dark and light mode.

### Internal
* Refactored auth screen layout to use `StackPane` overlay for absolute positioning of the language button.
* Added external CSS stylesheets (`scrollbar.css`, `scrollbar-light.css`) for scrollbar theming.
* README rewritten in English.

---

## ⚠️ Honest Security Notes

* "Stay logged in" is a trade-off: anyone logged into the same Windows account can open the vault (similar to saved browser passwords). For maximum security, leave the checkbox unchecked.
* The Windows clipboard history (`Win+V`) retains copies despite auto-clear.
* The app is not code-signed; SmartScreen/Gatekeeper will show a warning on first launch.
