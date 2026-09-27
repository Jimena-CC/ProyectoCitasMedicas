package com.medicitas.kiosk.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Resuelve la URL de la API para el kiosko, en este orden de prioridad:
 * 1. -Dapi.url=... (desarrollo, vía run-kiosk.bat/.sh o --demo-stub)
 * 2. %ProgramData%\MediCitasKiosk\kiosk.properties (configuración de TI, una
 * sola vez)
 * 3. ApiClient.DEFAULT_URL (localhost, para no romper nada si no hay
 * configuración)
 */
public final class KioskConfig {

    private static final Logger LOG = Logger.getLogger(KioskConfig.class.getName());
    private static final String CONFIG_DIR_NAME = "MediCitasKiosk";
    private static final String CONFIG_FILE_NAME = "kiosk.properties";

    private KioskConfig() {
    }

    public static String resolveApiUrl(String defaultUrl) {
        String fromSystemProperty = System.getProperty("api.url");
        if (fromSystemProperty != null && !fromSystemProperty.isBlank()) {
            return fromSystemProperty;
        }

        Path configFile = configFilePath();
        if (configFile != null && Files.isReadable(configFile)) {
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(configFile)) {
                props.load(in);
                String url = props.getProperty("api.url");
                if (url != null && !url.isBlank()) {
                    LOG.info(() -> "URL de la API leída de " + configFile + ": " + url);
                    return url.trim();
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "No se pudo leer " + configFile + ", se usa el valor por defecto", e);
            }
        }
        return defaultUrl;
    }

    private static Path configFilePath() {
        String programData = System.getenv("ProgramData");
        if (programData == null || programData.isBlank()) {
            return null; // no es Windows, o no existe la variable: se ignora
        }
        return Path.of(programData, CONFIG_DIR_NAME, CONFIG_FILE_NAME);
    }
}