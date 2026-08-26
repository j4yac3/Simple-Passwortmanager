package org.example;

import java.io.File;
import java.io.OutputStream;
import java.net.Socket;

public class Launcher {
    public static final int WAKE_PORT = 58742;
    private static java.nio.channels.FileLock instanceLock;
    private static java.io.RandomAccessFile lockChannel;

    public static void main(String[] args) {
        if (!acquireSingleInstance()) return;
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                ErrorLog.write("Unbehandelter Fehler (" + thread.getName() + ")", throwable));
        try {
            Main.main(args);
        } catch (Throwable t) {
            ErrorLog.showFatal("Programmstart", t);
        }
    }

    private static boolean acquireSingleInstance() {
        try {
            File dir = ErrorLog.appDir();
            lockChannel = new java.io.RandomAccessFile(new File(dir, "app.lock"), "rw");
            instanceLock = lockChannel.getChannel().tryLock();
            if (instanceLock == null) {
                if (wakeRunningInstance()) return false;
                javax.swing.JOptionPane.showMessageDialog(null,
                        "Der Passwortmanager laeuft bereits, ist aber nicht sichtbar.\n\n"
                                .concat("Loesung: Im Task-Manager alle Eintraege \"JM Passwortmanager\"\n")
                                .concat("beenden und dann erneut starten.\n\n")
                                .concat("The password manager is already running but not visible.\n\n")
                                .concat("Solution: In the Task Manager, end all \"JM Passwortmanager\"\n")
                                .concat("entries and then start it again."),
                        "JM Password Manager", javax.swing.JOptionPane.WARNING_MESSAGE);
                return false;
            }
        } catch (Throwable t) {
            return true;
        }
        return true;
    }

    private static boolean wakeRunningInstance() {
        try (Socket s = new Socket("127.0.0.1", WAKE_PORT)) {
            OutputStream out = s.getOutputStream();
            out.write('w');
            out.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
