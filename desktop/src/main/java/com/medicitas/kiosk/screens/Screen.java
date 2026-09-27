package com.medicitas.kiosk.screens;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Pantalla del kiosko que se muestra dentro del contenedor con barra de pasos y botones de navegación. */
public abstract class Screen {

    protected final KioskContext ctx;
    private Node content;

    protected Screen(KioskContext ctx) {
        this.ctx = ctx;
    }

    /** Contenido construido una sola vez y reutilizado entre visitas. */
    public final Node content() {
        if (content == null) {
            content = build();
        }
        return content;
    }

    protected abstract Node build();

    /** Se invoca cada vez que la pantalla pasa a ser visible. */
    public void onShow() {
    }

    /** Acción del botón principal. Debe llamar a {@code ctx.advance()} cuando corresponda. */
    public abstract void onNext(Button button);

    public void onBack() {
        ctx.goBack();
    }

    public String nextText() {
        return "Siguiente";
    }

    public boolean showNext() {
        return true;
    }

    public boolean showBack() {
        return true;
    }

    public boolean nextEnabled() {
        return true;
    }

    protected static Label title(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("title");
        l.setWrapText(true);
        return l;
    }

    protected static Label subtitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("subtitle");
        l.setWrapText(true);
        return l;
    }

    protected static Label label(String text, String... classes) {
        Label l = new Label(text);
        l.getStyleClass().addAll(classes);
        return l;
    }

    protected static VBox card(Node... children) {
        VBox v = new VBox(children);
        v.getStyleClass().add("section-card");
        return v;
    }
}
