package com.medicitas.kiosk.ui;

import com.medicitas.kiosk.api.ApiException;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Conecta las llamadas asíncronas a la API con el hilo de JavaFX. */
public final class Async {

    private static final String ORIGINAL_GRAPHIC = "async.originalGraphic";

    private Async() {
    }

    /**
     * Ejecuta la llamada y entrega el resultado o el error en el hilo de JavaFX.
     * Mientras dura, el botón indicado (puede ser nulo) queda deshabilitado con un indicador de carga.
     */
    public static <T> void execute(CompletableFuture<T> call, Button button, Consumer<T> onSuccess,
                                   Consumer<ApiException> onFailure) {
        loading(button, true);
        call.whenComplete((result, error) -> Platform.runLater(() -> {
            loading(button, false);
            if (error != null) {
                onFailure.accept(ApiException.from(error));
            } else {
                onSuccess.accept(result);
            }
        }));
    }

    public static void loading(Button button, boolean active) {
        if (button == null) {
            return;
        }
        if (active) {
            if (!button.getProperties().containsKey(ORIGINAL_GRAPHIC)) {
                button.getProperties().put(ORIGINAL_GRAPHIC, button.getGraphic());
            }
            ProgressIndicator spinner = new ProgressIndicator();
            spinner.setPrefSize(20, 20);
            spinner.setMaxSize(20, 20);
            button.setGraphic(spinner);
            button.setDisable(true);
        } else {
            Object original = button.getProperties().remove(ORIGINAL_GRAPHIC);
            button.setGraphic(original instanceof Node node ? node : null);
            button.setDisable(false);
        }
    }
}
