package org.example;

import java.util.HashMap;
import java.util.Map;

public final class I18n {

    private static String language = "de";
    private static final Map<String, String> EN = new HashMap<>();

    static {
        EN.put("Willkommen zurück", "Welcome back");
        EN.put("Bitte gib dein Master-Passwort ein, um den Tresor zu entsperren.", "Enter your master password to unlock your vault.");
        EN.put("Erstelle ein sicheres Master-Passwort, um deinen Tresor zu schützen.", "Create a strong master password to protect your vault.");
        EN.put("Tresor initialisieren", "Initialize vault");
        EN.put("Master-Passwort", "Master password");
        EN.put("Passwort bestätigen", "Confirm password");
        EN.put("Angemeldet bleiben", "Stay signed in");
        EN.put("Entsperren", "Unlock");
        EN.put("Falsches Master-Passwort!", "Wrong master password!");
        EN.put("Passwort vergessen?", "Forgot password?");
        EN.put("Passwort zurücksetzen", "Reset password");
        EN.put("Gib deinen Recovery-Code ein.", "Enter your recovery code.");
        EN.put("Recovery-Code", "Recovery code");
        EN.put("Neues Master-Passwort", "New master password");
        EN.put("Falscher oder ungültiger Recovery-Code.", "Wrong or invalid recovery code.");
        EN.put("Zurück zum Login", "Back to login");
        EN.put("WICHTIG: Recovery-Code", "IMPORTANT: Recovery code");
        EN.put("Speichere diesen Code sicher ab!\nEr ist die einzige Möglichkeit, dein Passwort zurückzusetzen.", "Store this code somewhere safe!\nIt is the only way to reset your password.");
        EN.put("Code kopieren", "Copy code");
        EN.put("Kopiert!", "Copied!");
        EN.put("Ich habe den Code sicher gespeichert", "I have saved this code safely");
        EN.put("Das Master-Passwort muss mindestens 12 Zeichen lang sein.", "The master password must be at least 12 characters long.");
        EN.put("Das Passwort braucht Groß- und Kleinbuchstaben sowie mindestens eine Zahl.", "The password needs uppercase and lowercase letters plus at least one number.");
        EN.put("Die Passwörter stimmen nicht überein.", "The passwords do not match.");
        EN.put("Systemfehler bei der Initialisierung.", "System error during initialization.");
        EN.put("Passwortmanager", "Password Manager");
        EN.put("Plattform (z.B. Google, GitHub)", "Platform (e.g. Google, GitHub)");
        EN.put("Website-Link (z.B. https://github.com) - Optional", "Website link (e.g. https://github.com) - optional");
        EN.put("Benutzername / E-Mail", "Username / email");
        EN.put("Passwort", "Password");
        EN.put("2FA-Schlüssel (optional, für Login-Codes)", "2FA key (optional, for login codes)");
        EN.put("Wie funktioniert 2FA?", "How does 2FA work?");
        EN.put("2FA schützt deine Konten mit einem zusätzlichen Code: einer 6-stelligen Zahl, die sich alle 30 Sekunden ändert.", "2FA protects your accounts with an extra code: a 6-digit number that changes every 30 seconds.");
        EN.put("So einfach gehts:\n1. Öffne die Website → Sicherheit → Zwei-Faktor-Aktivierung.\n2. Wähle \"Authenticator-App\", dann \"Kann nicht scannen?\" oder \"Schlüssel anzeigen\".\n3. Kopiere den angezeigten Schlüssel in das Feld hier.\n\nBeim Login fragt dich die Website danach nach dem aktuellen Code aus dieser App.", "It's that simple:\n1. Open the website → Security → Enable two-factor authentication.\n2. Choose \"Authenticator app\", then \"Can't scan?\" or \"Show setup key\".\n3. Paste the shown key into the field here.\n\nWhen logging in, the website will ask for the current code from this app.");
        EN.put("Alles klar!", "Got it!");
        EN.put("＋ Eintrag sicher verschlüsseln", "＋ Encrypt entry safely");
        EN.put("👤 Bitte zuerst einen Workspace erstellen", "👤 Please create a workspace first");
        EN.put("Plattform, Benutzername und Passwort dürfen nicht leer sein.", "Platform, username and password must not be empty.");
        EN.put("Noch keine Einträge für \"{0}\".\nFüge oben deinen ersten Eintrag hinzu.", "No entries for \"{0}\" yet.\nAdd your first entry above.");
        EN.put("Unbekannt", "Unknown");
        EN.put("🗑 Eintrag löschen", "🗑 Delete entry");
        EN.put("📋 Kopieren", "📋 Copy");
        EN.put("✓ Kopiert!", "✓ Copied!");
        EN.put("Der Link konnte nicht im Browser geöffnet werden.", "Could not open the link in your browser.");
        EN.put("Eintrag löschen?", "Delete entry?");
        EN.put("\"{0}\" wirklich löschen?\nDas kann nicht rückgängig gemacht werden.", "Really delete \"{0}\"?\nThis cannot be undone.");
        EN.put("Löschen", "Delete");
        EN.put("Konto löschen?", "Delete account?");
        EN.put("\"{0}\" und ALLE zugehörigen Einträge wirklich löschen?\nDas kann nicht rückgängig gemacht werden.", "Really delete \"{0}\" and ALL of its entries?\nThis cannot be undone.");
        EN.put("Alles löschen", "Delete all");
        EN.put("Hinweis", "Notice");
        EN.put("Verstanden", "Got it");
        EN.put("Abbrechen", "Cancel");
        EN.put("Bestätigen", "Confirm");
        EN.put("Speichern", "Save");
        EN.put("Erstellen", "Create");
        EN.put("Übernehmen", "Apply");
        EN.put("Passwort generieren", "Generate password");
        EN.put("🔄 Neu würfeln", "🔄 Reroll");
        EN.put("Großbuchstaben (A-Z)", "Uppercase (A-Z)");
        EN.put("Kleinbuchstaben (a-z)", "Lowercase (a-z)");
        EN.put("Zahlen (0-9)", "Digits (0-9)");
        EN.put("Sonderzeichen (!@#...)", "Special characters (!@#...)");
        EN.put("Länge: {0}", "Length: {0}");
        EN.put("Neuer Workspace", "New workspace");
        EN.put("Gib einen Namen für das neue Konto ein.", "Choose a name for the new account.");
        EN.put("z.B. Arbeit, Privat...", "e.g. Work, Personal...");
        EN.put("Dieses Konto existiert bereits!", "This account already exists!");
        EN.put("Sicherheitsprüfung", "Security check");
        EN.put("Bitte Master-Passwort eingeben:", "Please enter your master password:");
        EN.put("Eintrag bearbeiten", "Edit entry");
        EN.put("Passe die Felder an und speichere die Änderungen.", "Adjust the fields and save your changes.");
        EN.put("Plattform", "Platform");
        EN.put("URL (Optional)", "URL (optional)");
        EN.put("Benutzername", "Username");
        EN.put("Website erkannt", "Website detected");
        EN.put("🚀 Automatisch einloggen", "🚀 Sign in automatically");
        EN.put("Nur Website öffnen", "Only open website");
        EN.put("Benutzername kopiert", "Username copied");
        EN.put("Füge ihn jetzt im Login-Feld ein (Strg+V)...", "Paste it into the login field now (Ctrl+V)...");
        EN.put("② Passwort jetzt kopieren", "② Copy password now");
        EN.put("Fertig", "Done");
        EN.put("Passwort kopiert!", "Password copied!");
        EN.put("Füge es jetzt im Browser ein (Strg+V).", "Paste it into the browser now (Ctrl+V).");
        EN.put("Das 2FA-Secret ist ungültig.\nErlaubt ist ein Base32-Secret oder ein otpauth://-Link.", "This 2FA key is invalid.\nUse a Base32 secret or an otpauth:// link.");
    }

    private I18n() {}

    public static void setLanguage(String lang) {
        language = "en".equalsIgnoreCase(lang) ? "en" : "de";
    }

    public static String getLanguage() {
        return language;
    }

    public static boolean isEnglish() {
        return "en".equals(language);
    }

    public static String tr(String german, Object... args) {
        String s = isEnglish() ? EN.getOrDefault(german, german) : german;
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                s = s.replace("{" + i + "}", String.valueOf(args[i]));
            }
        }
        return s;
    }
}
