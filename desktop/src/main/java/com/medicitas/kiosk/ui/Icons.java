package com.medicitas.kiosk.ui;


import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Scale;

import java.util.List;
import java.util.Map;

/**
 * Iconos de Iconoir (https://iconoir.com, licencia MIT) incrustados como trazos vectoriales.
 *
 * <p>Se embeben en lugar de agregar una dependencia: Iconoir no publica un paquete para JavaFX.
 * El color se define en el CSS con {@code -fx-stroke} sobre la clase {@code .icon}.</p>
 */
public final class Icons {

    /** Lienzo original de Iconoir: todos los trazos están dibujados sobre 24x24. */
    private static final double CANVAS = 24;

    private record Stroke(String path, boolean filled) { }

    private static final Map<String, List<Stroke>> STROKES = Map.ofEntries(
            Map.entry("arrow-left", List.of(new Stroke("M21 12L3 12M3 12L11.5 3.5M3 12L11.5 20.5", false))),
            Map.entry("arrow-right", List.of(new Stroke("M3 12L21 12M21 12L12.5 3.5M21 12L12.5 20.5", false))),
            Map.entry("calendar-check", List.of(new Stroke("M13 21H5C3.89543 21 3 20.1046 3 19V10H21V15M15 4V2M15 4V6M15 4H10.5", false), new Stroke("M3 10V6C3 4.89543 3.89543 4 5 4H7", false), new Stroke("M7 2V6", false), new Stroke("M21 10V6C21 4.89543 20.1046 4 19 4H18.5", false), new Stroke("M16 20L18 22L22 18", false))),
            Map.entry("calendar", List.of(new Stroke("M15 4V2M15 4V6M15 4H10.5M3 10V19C3 20.1046 3.89543 21 5 21H19C20.1046 21 21 20.1046 21 19V10H3Z", false), new Stroke("M3 10V6C3 4.89543 3.89543 4 5 4H7", false), new Stroke("M7 2V6", false), new Stroke("M21 10V6C21 4.89543 20.1046 4 19 4H18.5", false))),
            Map.entry("check-circle", List.of(new Stroke("M7 12.5L10 15.5L17 8.5", false), new Stroke("M12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22Z", false))),
            Map.entry("check", List.of(new Stroke("M5 13L9 17L19 7", false))),
            Map.entry("circle", List.of(new Stroke("M12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22Z", false))),
            Map.entry("clock", List.of(new Stroke("M12 6L12 12L18 12", false), new Stroke("M12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22Z", false))),
            Map.entry("erase", List.of(new Stroke("M21 21L9 21", false), new Stroke("M15.889 14.8891L8.46436 7.46448", false), new Stroke("M2.8934 12.6066L12.0858 3.41421C12.8668 2.63317 14.1332 2.63317 14.9142 3.41421L19.864 8.36396C20.645 9.14501 20.645 10.4113 19.864 11.1924L10.6213 20.435C10.2596 20.7968 9.76894 21 9.25736 21C8.74577 21 8.25514 20.7968 7.8934 20.435L2.8934 15.435C2.11235 14.654 2.11235 13.3877 2.8934 12.6066Z", false))),
            Map.entry("help-circle", List.of(new Stroke("M12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22Z", false), new Stroke("M9 9C9 5.49997 14.5 5.5 14.5 9C14.5 11.5 12 10.9999 12 13.9999", false), new Stroke("M12 18.01L12.01 17.9989", false))),
            Map.entry("hospital-circle", List.of(new Stroke("M12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22Z", false), new Stroke("M8 12C10.6667 12 13.3333 12 16 12M8 12V7M8 12V17M16 12V17M16 12V7", false))),
            Map.entry("info-circle", List.of(new Stroke("M12 11.5V16.5", false), new Stroke("M12 7.51L12.01 7.49889", false), new Stroke("M12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22Z", false))),
            Map.entry("list", List.of(new Stroke("M8 6L20 6", false), new Stroke("M4 6.01L4.01 5.99889", false), new Stroke("M4 12.01L4.01 11.9989", false), new Stroke("M4 18.01L4.01 17.9989", false), new Stroke("M8 12L20 12", false), new Stroke("M8 18L20 18", false))),
            Map.entry("mail", List.of(new Stroke("M7 9L12 12.5L17 9", false), new Stroke("M2 17V7C2 5.89543 2.89543 5 4 5H20C21.1046 5 22 5.89543 22 7V17C22 18.1046 21.1046 19 20 19H4C2.89543 19 2 18.1046 2 17Z", false))),
            Map.entry("map-pin", List.of(new Stroke("M20 10C20 14.4183 12 22 12 22C12 22 4 14.4183 4 10C4 5.58172 7.58172 2 12 2C16.4183 2 20 5.58172 20 10Z", false), new Stroke("M12 11C12.5523 11 13 10.5523 13 10C13 9.44772 12.5523 9 12 9C11.4477 9 11 9.44772 11 10C11 10.5523 11.4477 11 12 11Z", true))),
            Map.entry("nav-arrow-down", List.of(new Stroke("M6 9L12 15L18 9", false))),
            Map.entry("nav-arrow-left", List.of(new Stroke("M15 6L9 12L15 18", false))),
            Map.entry("nav-arrow-right", List.of(new Stroke("M9 6L15 12L9 18", false))),
            Map.entry("page-edit", List.of(new Stroke("M20 12V5.74853C20 5.5894 19.9368 5.43679 19.8243 5.32426L16.6757 2.17574C16.5632 2.06321 16.4106 2 16.2515 2H4.6C4.26863 2 4 2.26863 4 2.6V21.4C4 21.7314 4.26863 22 4.6 22H11", false), new Stroke("M8 10H16M8 6H12M8 14H11", false), new Stroke("M17.9541 16.9394L18.9541 15.9394C19.392 15.5015 20.102 15.5015 20.5399 15.9394V15.9394C20.9778 16.3773 20.9778 17.0873 20.5399 17.5252L19.5399 18.5252M17.9541 16.9394L14.963 19.9305C14.8131 20.0804 14.7147 20.2741 14.6821 20.4835L14.4394 22.0399L15.9957 21.7973C16.2052 21.7646 16.3988 21.6662 16.5487 21.5163L19.5399 18.5252M17.9541 16.9394L19.5399 18.5252", false), new Stroke("M16 2V5.4C16 5.73137 16.2686 6 16.6 6H20", false))),
            Map.entry("privacy-policy", List.of(new Stroke("M20 12V5.74853C20 5.5894 19.9368 5.43679 19.8243 5.32426L16.6757 2.17574C16.5632 2.06321 16.4106 2 16.2515 2H4.6C4.26863 2 4 2.26863 4 2.6V21.4C4 21.7314 4.26863 22 4.6 22H13", false), new Stroke("M8 10H16M8 6H12M8 14H11", false), new Stroke("M16 2V5.4C16 5.73137 16.2686 6 16.6 6H20", false), new Stroke("M19.9923 15.125L22.5477 15.774C22.8137 15.8416 23.0013 16.0833 22.9931 16.3576C22.8214 22.1159 19.5 23 19.5 23C19.5 23 16.1786 22.1159 16.0069 16.3576C15.9987 16.0833 16.1863 15.8416 16.4523 15.774L19.0077 15.125C19.3308 15.043 19.6692 15.043 19.9923 15.125Z", false))),
            Map.entry("refresh", List.of(new Stroke("M21.8883 13.5C21.1645 18.3113 17.013 22 12 22C6.47715 22 2 17.5228 2 12C2 6.47715 6.47715 2 12 2C16.1006 2 19.6248 4.46819 21.1679 8", false), new Stroke("M17 8H21.4C21.7314 8 22 7.73137 22 7.4V3", false))),
            Map.entry("search", List.of(new Stroke("M17 17L21 21", false), new Stroke("M3 11C3 15.4183 6.58172 19 11 19C13.213 19 15.2161 18.1015 16.6644 16.6493C18.1077 15.2022 19 13.2053 19 11C19 6.58172 15.4183 3 11 3C6.58172 3 3 6.58172 3 11Z", false))),
            Map.entry("shield-check", List.of(new Stroke("M8.5 11.5L11.5 14.5L16.5 9.5", false), new Stroke("M5 18L3.13036 4.91253C3.05646 4.39524 3.39389 3.91247 3.90398 3.79912L11.5661 2.09641C11.8519 2.03291 12.1481 2.03291 12.4339 2.09641L20.096 3.79912C20.6061 3.91247 20.9435 4.39524 20.8696 4.91252L19 18C18.9293 18.495 18.5 21.5 12 21.5C5.5 21.5 5.07071 18.495 5 18Z", false))),
            Map.entry("user", List.of(new Stroke("M5 20V19C5 15.134 8.13401 12 12 12V12C15.866 12 19 15.134 19 19V20", false), new Stroke("M12 12C14.2091 12 16 10.2091 16 8C16 5.79086 14.2091 4 12 4C9.79086 4 8 5.79086 8 8C8 10.2091 9.79086 12 12 12Z", false))),
            Map.entry("warning-circle", List.of(new Stroke("M12 7L12 13", false), new Stroke("M12 17.01L12.01 16.9989", false), new Stroke("M12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22Z", false))),
            Map.entry("xmark", List.of(new Stroke("M6.75827 17.2426L12.0009 12M17.2435 6.75736L12.0009 12M12.0009 12L6.75827 6.75736M12.0009 12L17.2435 17.2426", false)))
    );


    private Icons() {
    }

    /** Icono del tamaño indicado, listo para usarse como gráfico de un control. */
    public static Node of(String name, double size, String... extraClasses) {
        List<Stroke> strokes = STROKES.get(name);
        if (strokes == null) {
            throw new IllegalArgumentException("Icono desconocido: " + name);
        }
        Group group = new Group();
        for (Stroke stroke : strokes) {
            SVGPath shape = new SVGPath();
            shape.setContent(stroke.path());
            shape.getStyleClass().add(stroke.filled() ? "icon-fill" : "icon-stroke");
            shape.setStrokeLineCap(StrokeLineCap.ROUND);
            shape.setStrokeLineJoin(StrokeLineJoin.ROUND);
            group.getChildren().add(shape);
        }
        group.getTransforms().add(new Scale(size / CANVAS, size / CANVAS));

        Pane box = new Pane(group);
        box.getStyleClass().add("icon");
        box.getStyleClass().addAll(extraClasses);
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        box.setMouseTransparent(true);
        return box;
    }
}
