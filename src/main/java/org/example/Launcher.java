package org.example;

public class Launcher {
    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                ErrorLog.write("Unbehandelter Fehler (" + thread.getName() + ")", throwable));
        try {
            Main.main(args);
        } catch (Throwable t) {
            ErrorLog.showFatal("Programmstart", t);
        }
    }
}
