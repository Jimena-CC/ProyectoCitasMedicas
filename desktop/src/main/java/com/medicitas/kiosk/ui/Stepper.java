package com.medicitas.kiosk.ui;

import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/** Barra de pasos vertical: completados con check, el actual resaltado y los pendientes atenuados. */
public class Stepper extends VBox {

    private static final PseudoClass DONE = PseudoClass.getPseudoClass("done");
    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
    private static final PseudoClass LOCKED = PseudoClass.getPseudoClass("locked");

    private final List<HBox> rows = new ArrayList<>();
    private final List<StackPane> circles = new ArrayList<>();
    private final List<Region> lines = new ArrayList<>();
    private IntConsumer onSelect = i -> { };
    private int max;

    public Stepper(List<String> titles) {
        getStyleClass().add("stepper");
        for (int i = 0; i < titles.size(); i++) {
            final int index = i;
            StackPane circle = new StackPane();
            circle.getStyleClass().add("step-circle");
            Label title = new Label(titles.get(i));
            title.getStyleClass().add("step-title");
            HBox row = new HBox(circle, title);
            row.getStyleClass().add("step");
            row.setOnMouseClicked(e -> {
                if (index <= max) {
                    onSelect.accept(index);
                }
            });
            rows.add(row);
            circles.add(circle);
            getChildren().add(row);

            if (i < titles.size() - 1) {
                Region line = new Region();
                line.getStyleClass().add("step-line");
                VBox.setMargin(line, new Insets(0, 0, 0, 23));
                lines.add(line);
                getChildren().add(line);
            }
        }
        update(0, 0);
    }

    /** @param current índice del paso visible · @param maxReached último paso al que se puede volver */
    public final void update(int current, int maxReached) {
        this.max = maxReached;
        for (int i = 0; i < rows.size(); i++) {
            boolean active = i == current;
            boolean done = i < current || (i <= maxReached && !active && i < maxReached);
            HBox row = rows.get(i);
            row.pseudoClassStateChanged(ACTIVE, active);
            row.pseudoClassStateChanged(DONE, done);
            row.pseudoClassStateChanged(LOCKED, i > maxReached);

            StackPane circle = circles.get(i);
            if (done) {
                circle.getChildren().setAll(Icons.of("check", 20));
            } else {
                circle.getChildren().setAll(new Label(String.valueOf(i + 1)));
            }
            if (i < lines.size()) {
                lines.get(i).pseudoClassStateChanged(DONE, i < current);
            }
        }
    }

    public void setOnStepSelected(IntConsumer action) {
        this.onSelect = action;
    }
}
