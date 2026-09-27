package com.medicitas.kiosk.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Tarjeta seleccionable para elegir aseguradora o atención particular. */
public class InsurerCard extends ToggleButton {

    private final StackPane mark = new StackPane(Icons.of("circle", 24));

    public InsurerCard(String title, String subtitle) {
        getStyleClass().add("insurer-card");
        Label t = new Label(title);
        t.getStyleClass().add("insurer-title");
        Label s = new Label(subtitle);
        s.getStyleClass().add("insurer-sub");
        s.setWrapText(true);
        VBox texts = new VBox(2, t, s);
        HBox.setHgrow(texts, Priority.ALWAYS);
        HBox content = new HBox(12, texts, mark);
        content.setAlignment(Pos.CENTER_LEFT);
        setGraphic(content);
        setMaxWidth(Double.MAX_VALUE);
        // Altura uniforme: sin esto las tarjetas se estiran y se cortan al hacer scroll.
        setPrefHeight(88);
        setMinHeight(88);
        setMaxHeight(88);
        setAccessibleText(title + ". " + subtitle);
        selectedProperty().addListener((obs, before, now) ->
                mark.getChildren().setAll(Icons.of(now ? "check-circle" : "circle", 24)));
    }
}
