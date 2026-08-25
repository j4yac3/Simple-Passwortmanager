package org.example;

import javax.swing.JOptionPane;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

public final class ErrorLog {

    private static File logFile() {
        return new File(appDir(), "error.log");
    }

    public static File appDir() {
        String home = System.getProperty("user.home");
        File dir = new File(home, ".jm-passwortmanager");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static synchronized void write(String context, Throwable t) {
        try (PrintWriter out = new PrintWriter(new FileWriter(logFile(), StandardCharsets.UTF_8, true))) {
            out.println("==== " + LocalDateTime.now() + " | " + context);
            if (t != null) {
                out.println(t.getClass().getName() + ": " + t.getMessage());
                for (StackTraceElement e : t.getStackTrace()) out.println("    at " + e);
                if (t.getCause() != null) {
                    out.println("CAUSE: " + t.getCause());
                    for (StackTraceElement e : t.getCause().getStackTrace()) out.println("    at " + e);
                }
            } else {
                out.println("(keine Details)");
            }
            out.println();
        } catch (Exception ignored) {}
    }

    public static void showFatal(String context, Throwable t) {
        write(context, t);
        try {
            JOptionPane.showMessageDialog(null,
                    "Es ist ein Fehler aufgetreten.\nDetails wurden gespeichert in:\n"
                            .concat(logFile().getAbsolutePath()),
                    "JM Passwortmanager", JOptionPane.ERROR_MESSAGE);
        } catch (Throwable ignored) {}
    }
}
