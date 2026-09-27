package com.medicitas.kiosk.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Tarjeta que resume la selección actual del paciente. */
public class SummaryCard extends VBox {

    private final Label value = new Label();
    private final Node icon;

    public SummaryCard(String label, String iconName) {
        getStyleClass().add("summary-card");
        Label title = new Label(label);
        title.getStyleClass().add("summary-label");
        icon = Icons.of(iconName, 22);
        value.getStyleClass().add("summary-value");
        value.setWrapText(true);
        HBox row = new HBox(12, icon, value);
        row.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(title, row);
    }

    public void setValue(String text) {
        value.setText(text);
        icon.setVisible(true);
        icon.setManaged(true);
        value.getStyleClass().remove("muted");
    }

    public void setEmpty(String hint) {
        value.setText(hint);
        icon.setVisible(false);
        icon.setManaged(false);
        if (!value.getStyleClass().contains("muted")) {
            value.getStyleClass().add("muted");
        }
    }
}
