package com.medicitas.kiosk.ui;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

/** Aviso en línea para errores de la API o información al paciente. Nunca muestra detalles técnicos. */
public class Banner extends HBox {

    private final StackPane icon = new StackPane(Icons.of("warning-circle", 22));
    private final Label text = new Label();
    private final Button action = new Button("Reintentar");

    public Banner() {
        getStyleClass().add("banner");
        text.getStyleClass().add("banner-text");
        text.setWrapText(true);
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);
        action.getStyleClass().add("link-button");
        getChildren().addAll(icon, text, action);
        managedProperty().bind(visibleProperty());
        hide();
    }

    public void error(String message, Runnable retry) {
        show("error", "warning-circle", message, retry);
    }

    public void info(String message) {
        show("info", "info-circle", message, null);
    }

    public void hide() {
        setVisible(false);
    }

    private void show(String type, String iconName, String message, Runnable retry) {
        getStyleClass().removeAll("error", "info");
        getStyleClass().add(type);
        icon.getChildren().setAll(Icons.of(iconName, 22));
        text.setText(message);
        action.setVisible(retry != null);
        action.setManaged(retry != null);
        action.setOnAction(e -> {
            hide();
            if (retry != null) {
                retry.run();
            }
        });
        setVisible(true);
    }
}
