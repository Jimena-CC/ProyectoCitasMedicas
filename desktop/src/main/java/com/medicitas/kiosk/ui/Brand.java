package com.medicitas.kiosk.ui;

import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;

/**
 * Emblema de la Clínica Anglo Americana, tomado de su logotipo oficial:
 * la cruz inscrita en el círculo, rodeada por las ocho figuras radiales y el anillo exterior.
 */
public final class Brand {

    /** Cruz, disco y figuras radiales. */
    private static final String EMBLEM = "M173.03 101.44c-31.02 0-56.17 25.15-56.17 56.17s25.15 56.17 56.17 56.17 56.17-25.15 56.17-56.17-25.15-56.17-56.17-56.17Zm32.55 67.99h-20.74v20.74h-23.63v-20.74h-20.74V145.8h20.74v-20.74h23.63v20.74h20.74v23.63ZM105.68 115.5a79.876 79.876 0 0 1 26.21-25.86c.79-3.55 1.01-7.5.04-11.59-3.31-14.02-21.11-27.73-28.36-6.46 0 0-13.55-1.58-13.71 14.18 0 0-16.07-.95-12.92 15.91 2.3 12.31 18.61 14.03 28.73 13.82ZM257.68 85.77c-.16-15.75-13.71-14.18-13.71-14.18-7.25-21.27-25.05-7.56-28.36 6.46-1.07 4.52-.69 8.88.31 12.71a79.758 79.758 0 0 1 24.43 24.7c9.91.41 27.82-.83 30.24-13.77 3.15-16.86-12.92-15.91-12.92-15.91ZM270.6 214.18c-2.45-13.12-20.85-14.21-30.66-13.76a79.916 79.916 0 0 1-23.8 23.91c-1.15 4.01-1.66 8.65-.52 13.48 3.31 14.02 21.11 27.73 28.36 6.46 0 0 13.55 1.58 13.71-14.18 0 0 16.07.95 12.92-15.91ZM106.09 200.37c-10.08-.26-26.81 1.34-29.14 13.81-3.15 16.86 12.92 15.91 12.92 15.91.16 15.75 13.71 14.18 13.71 14.18 7.25 21.27 25.05 7.56 28.36-6.46 1.03-4.37.71-8.59-.21-12.33-10.34-6.31-19.1-14.92-25.63-25.11ZM282.79 147.76c5.46-18.32-7.07-18.16-7.07-18.16-3.86 12.39-18.77 17.71-23.75 19.17.32 2.9.5 5.85.5 8.84 0 2.64-.14 5.25-.39 7.82 5.1 1.5 19.8 6.84 23.64 19.13 0 0 12.54.16 7.07-18.16 0 0 11.09 1.61 12.7-9.32-1.61-10.93-12.7-9.32-12.7-9.32ZM164.67 78.61c2.75-.29 5.54-.44 8.36-.44 2.66 0 5.28.14 7.88.39 1.14-4.15 6.35-20.36 19.36-24.42 0 0 .16-12.54-18.16-7.07 0 0 1.61-11.09-9.32-12.7-10.93 1.61-9.32 12.7-9.32 12.7-18.32-5.46-18.16 7.07-18.16 7.07 13.05 4.07 18.26 20.39 19.37 24.47ZM93.59 157.61c0-2.94.17-5.84.48-8.7-4.42-1.23-20.23-6.48-24.23-19.31 0 0-12.54-.16-7.07 18.16 0 0-11.09-1.61-12.7 9.32 1.61 10.93 12.7 9.32 12.7 9.32-5.46 18.32 7.07 18.16 7.07 18.16 3.97-12.72 19.56-17.99 24.12-19.28a81.26 81.26 0 0 1-.38-7.68ZM181.2 236.64c-2.69.27-5.41.42-8.17.42a79.6 79.6 0 0 1-8.65-.48c-1.58 5.27-6.94 19.66-19.08 23.45 0 0-.16 12.54 18.16 7.07 0 0-1.61 11.09 9.32 12.7 10.93-1.61 9.32-12.7 9.32-12.7 18.32 5.46 18.16-7.07 18.16-7.07-12.1-3.77-17.46-18.07-19.06-23.39Z";

    /** Anillo exterior. */
    private static final String RING = "M173.03 78.17c-43.88 0-79.44 35.57-79.44 79.44s35.57 79.44 79.44 79.44 79.44-35.57 79.44-79.44-35.57-79.44-79.44-79.44Zm0 149.06c-38.45 0-69.62-31.17-69.62-69.62s31.17-69.62 69.62-69.62 69.62 31.17 69.62 69.62-31.17 69.62-69.62 69.62Z";

    private Brand() {
    }

    public static Node mark(double size) {
        SVGPath shapes = new SVGPath();
        shapes.setContent(EMBLEM);
        shapes.getStyleClass().add("brand-shape");
        SVGPath ring = new SVGPath();
        ring.setContent(RING);
        ring.getStyleClass().add("brand-shape");

        Group group = new Group(shapes, ring);
        Bounds original = group.getBoundsInLocal();
        double scale = size / Math.max(original.getWidth(), original.getHeight());
        group.getTransforms().add(new Scale(scale, scale));
        group.setLayoutX(-original.getMinX() * scale);
        group.setLayoutY(-original.getMinY() * scale);

        Pane box = new Pane(group);
        box.getStyleClass().add("brand");
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        box.setMouseTransparent(true);
        return box;
    }

    public static Node logo() {
        Label name = new Label("MediCitas");
        name.getStyleClass().add("brand-name");
        Label sub = new Label("Clínica Anglo Americana");
        sub.getStyleClass().add("brand-sub");
        VBox texts = new VBox(0, name, sub);
        HBox box = new HBox(14, mark(46), texts);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }
}
