package com.medicitas.kiosk.app;

import javafx.application.Application;

/**
 * Punto de entrada. Se separa de {@link KioskApp} para que el JAR también arranque
 * con {@code java -jar} cuando JavaFX está en el classpath.
 */
public final class KioskLauncher {

    private KioskLauncher() {
    }

    public static void main(String[] args) {
        Application.launch(KioskApp.class, args);
    }
}
