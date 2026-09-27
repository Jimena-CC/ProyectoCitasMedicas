package com.medicitas.kiosk.ui;

import javafx.scene.text.Font;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Registra las fuentes incluidas en el ejecutable antes de construir la escena. */
public final class Fonts {

    private static final Logger LOG = Logger.getLogger(Fonts.class.getName());
    private static final String[] FILES = {
            "Onest-Regular.ttf", "Onest-Medium.ttf", "Onest-SemiBold.ttf", "Onest-Bold.ttf",
            "IBMPlexMono-Medium.ttf"
    };

    private Fonts() {
    }

    public static void load() {
        for (String file : FILES) {
            try (InputStream in = Fonts.class.getResourceAsStream("/fonts/" + file)) {
                if (in == null) {
                    LOG.warning(() -> "No se encontró la fuente " + file);
                    continue;
                }
                Font font = Font.loadFont(in, 16);
                if (font != null) {
                    LOG.fine(() -> "Fuente cargada: " + font.getName() + " (" + font.getFamily() + ")");
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "No se pudo cargar la fuente " + file, e);
            }
        }
    }
}
