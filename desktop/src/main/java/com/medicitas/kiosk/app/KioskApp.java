package com.medicitas.kiosk.app;

import atlantafx.base.theme.PrimerLight;
import com.medicitas.kiosk.api.ApiClient;
import com.medicitas.kiosk.screens.KioskShell;
import com.medicitas.kiosk.ui.Fonts;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Aplicación JavaFX del Kiosko de Autoatención.
 * Argumentos: {@code --kiosk} abre en pantalla completa; {@code --demo-stub} levanta una API simulada local.
 */
public class KioskApp extends Application {

    private static final Logger LOG = Logger.getLogger(KioskApp.class.getName());
    private static final String STUB_CLASS = "com.medicitas.kiosk.dev.DemoStub";
    private static final String CAPTURE_CLASS = "com.medicitas.kiosk.dev.DemoCapture";

    private AutoCloseable stub;

    @Override
    public void start(Stage stage) {
        List<String> args = getParameters().getRaw();
        if (args.contains("--demo-stub")) {
            startStub();
        }

        Fonts.load();
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        KioskShell shell = new KioskShell(ApiClient.fromProperties());
        Scene scene = new Scene(shell.root(), 1440, 900);
        scene.getStylesheets().add(KioskApp.class.getResource("/styles/kiosk.css").toExternalForm());

        stage.setTitle("MediCitas Anglo · Kiosko de Autoatención");
        stage.setMinWidth(1280);
        stage.setMinHeight(760);
        stage.setScene(scene);
        if (args.contains("--kiosk")) {
            stage.setFullScreenExitHint("");
            stage.setFullScreen(true);
        }
        stage.show();
        shell.start(scene);

        args.stream().filter(a -> a.startsWith("--capture=")).findFirst()
                .ifPresent(arg -> capture(shell, scene, arg.substring("--capture=".length())));
    }

    /** El stub vive en el paquete dev y se carga por reflexión: no forma parte del flujo normal. */
    private void startStub() {
        try {
            Object instance = Class.forName(STUB_CLASS).getMethod("start").invoke(null);
            stub = (AutoCloseable) instance;
            String url = (String) instance.getClass().getMethod("url").invoke(instance);
            System.setProperty("api.url", url);
            LOG.info(() -> "API simulada disponible en " + url);
        } catch (ReflectiveOperationException e) {
            LOG.log(Level.WARNING, "No se pudo iniciar la API simulada", e);
        }
    }

    /** Captura de pantallas para revisar el diseño. Igual que el stub, vive en el paquete dev. */
    private void capture(KioskShell shell, Scene scene, String folder) {
        try {
            Class.forName(CAPTURE_CLASS)
                    .getMethod("schedule", KioskShell.class, Scene.class, String.class)
                    .invoke(null, shell, scene, folder);
        } catch (ReflectiveOperationException e) {
            LOG.log(Level.WARNING, "No se pudo iniciar la captura de pantallas", e);
        }
    }

    @Override
    public void stop() throws Exception {
        if (stub != null) {
            stub.close();
        }
    }
}
