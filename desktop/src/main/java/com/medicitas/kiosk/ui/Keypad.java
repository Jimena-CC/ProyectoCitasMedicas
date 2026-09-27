package com.medicitas.kiosk.ui;

import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;

import java.util.function.Consumer;

/** Teclado numérico en pantalla para ingresar el documento sin teclado físico. */
public class Keypad extends GridPane {

    public Keypad(Consumer<String> onDigit, Runnable onBackspace, Runnable onClear) {
        getStyleClass().add("keypad");
        String[] digits = {"1", "2", "3", "4", "5", "6", "7", "8", "9"};
        for (int i = 0; i < digits.length; i++) {
            String d = digits[i];
            add(key(d, () -> onDigit.accept(d)), i % 3, i / 3);
        }
        Button clear = key("Limpiar", onClear);
        clear.getStyleClass().add("secondary");
        add(clear, 0, 3);
        add(key("0", () -> onDigit.accept("0")), 1, 3);
        Button backspace = key("", onBackspace);
        backspace.setGraphic(Icons.of("erase", 24));
        backspace.setAccessibleText("Borrar último dígito");
        backspace.getStyleClass().add("secondary");
        add(backspace, 2, 3);
    }

    private static Button key(String text, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add("keypad-key");
        b.setFocusTraversable(false);
        b.setOnAction(e -> action.run());
        return b;
    }
}
