package org.example;

import java.io.File;

public class Launcher {
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
                javax.swing.JOptionPane.showMessageDialog(null,
                        "Der Passwortmanager ist bereits geoeffnet.\nBitte im bereits geoeffneten Fenster weiterarbeiten.",
                        "JM Passwortmanager", javax.swing.JOptionPane.INFORMATION_MESSAGE);
                return false;
            }
        } catch (Throwable t) {
            return true;
        }
        return true;
    }
}
