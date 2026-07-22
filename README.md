# 🔒 JM Passwortmanager

Ein moderner, komplett lokaler und sicherer Passwortmanager mit einer eleganten, animierten Benutzeroberfläche. Geschrieben in Java mit JavaFX für ein flüssiges, modernes UI/UX-Erlebnis.

Dieses Projekt speichert **keine Daten in der Cloud**. Du hast die 100%ige Kontrolle über deine verschlüsselte Datenbank, die vollständig offline auf deinem Rechner liegt.

---

## ✨ Features

* **🛡️ Höchste Sicherheit:** Alle Passwörter, Benutzernamen und Plattformen werden mit einer starken **AES-256-Verschlüsselung** in der Datenbank abgelegt.
* **📂 Workspaces / Konten:** Erstelle verschiedene Konten oder Bereiche (z. B. "Privat", "Arbeit"), um deine Einträge sauber voneinander zu trennen.
* **💾 100% Lokal:** Keine externen Server, keine Cloud-Anbindung. Alles läuft über eine lokale, schlanke **SQLite-Datenbank** (`vault.db`).
* **🌓 Dark & Light Mode:** Ein modernes, abgerundetes Design (im lila-schwarzen Premium-Look), das sich per Knopfdruck dynamisch anpassen lässt.
* **📱 Swipe-to-Delete Gesten:** Intuitiv bedienbare, animierte Karten, die sich wie auf dem Smartphone zur Seite wischen lassen, um Einträge oder Konten zu löschen.
* **📋 Auto-Clear Clipboard:** Aus Sicherheitsgründen wird ein in die Zwischenablage kopiertes Passwort nach 15 Sekunden automatisch gelöscht.
* **🎲 Passwort-Generator:** Integrierter Generator für extrem sichere und kryptische Passwörter mit anpassbaren Parametern.

---

## 🛠️ Tech-Stack

* **Sprache:** Java 17+
* **Framework:** JavaFX 17.0.10+ (für das moderne Frontend)
* **Datenbank:** SQLite (über JDBC)
* **Build-Tool:** Maven

---

## 🚀 Installation & Start

### Voraussetzungen
Stelle sicher, dass du ein **Java Development Kit (JDK 17 oder neuer)** installiert hast.

### 1. Repository klonen
```bash
git clone https://github.com/j4yac3/Simple-Passwortmanager.git](https://github.com/j4yac3/Simple-Passwortmanager.git)
cd Simple-Passwortmanager
