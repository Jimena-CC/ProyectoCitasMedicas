package com.medicitas.kiosk.ui;

import com.medicitas.kiosk.api.Dtos;
import com.medicitas.kiosk.screens.Dates;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Franja de fechas de 7 días por página: "Hoy", "Mañana", "mié 16"... con subrayado en el día elegido.
 * Los días sin cupos libres quedan deshabilitados.
 */
public class DateStrip extends VBox {

    public static final int DAYS_PER_PAGE = 7;
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private final HBox row = new HBox();
    private final Label month = new Label();
    private final Button monthPrevious = chevron("nav-arrow-left", "Semana anterior");
    private final Button monthNext = chevron("nav-arrow-right", "Semana siguiente");
    private final Button rowPrevious = chevron("nav-arrow-left", "Semana anterior");
    private final Button rowNext = chevron("nav-arrow-right", "Semana siguiente");

    private List<Dtos.AvailableDay> days = List.of();
    private LocalDate today = LocalDate.now();
    private LocalDate selected;
    private int page;
    private Consumer<LocalDate> onSelect = d -> { };

    public DateStrip() {
        setSpacing(18);
        month.getStyleClass().add("month-label");

        monthPrevious.setOnAction(e -> changePage(-1));
        monthNext.setOnAction(e -> changePage(1));
        rowPrevious.setOnAction(e -> changePage(-1));
        rowNext.setOnAction(e -> changePage(1));

        row.getStyleClass().add("day-strip");
        HBox.setHgrow(row, Priority.ALWAYS);
        HBox container = new HBox(4, rowPrevious, row, rowNext);
        container.setAlignment(Pos.CENTER);
        getChildren().add(container);
    }

    /** Selector de mes para colocarlo junto al título de la tarjeta. */
    public HBox monthSelector() {
        HBox box = new HBox(monthPrevious, month, monthNext);
        box.getStyleClass().add("month-switch");
        return box;
    }

    public void setDays(List<Dtos.AvailableDay> newDays, LocalDate today) {
        this.days = newDays == null ? List.of() : List.copyOf(newDays);
        this.today = today;
        if (selected != null) {
            int index = indexOf(selected);
            page = index >= 0 ? index / DAYS_PER_PAGE : 0;
        } else {
            page = 0;
        }
        render();
    }

    public void select(LocalDate date) {
        this.selected = date;
        int index = indexOf(date);
        if (index >= 0) {
            page = index / DAYS_PER_PAGE;
        }
        render();
    }

    public LocalDate getSelected() {
        return selected;
    }

    /** Primer día con cupos libres, útil para preseleccionar. */
    public LocalDate firstDayWithSlots() {
        return days.stream().filter(d -> d.freeSlots() > 0).map(Dtos.AvailableDay::date).findFirst().orElse(null);
    }

    public void setOnDateSelected(Consumer<LocalDate> action) {
        this.onSelect = action;
    }

    private void changePage(int delta) {
        int pages = Math.max(1, (int) Math.ceil(days.size() / (double) DAYS_PER_PAGE));
        page = Math.max(0, Math.min(pages - 1, page + delta));
        render();
    }

    private void render() {
        List<Button> buttons = new ArrayList<>();
        int from = page * DAYS_PER_PAGE;
        int to = Math.min(days.size(), from + DAYS_PER_PAGE);
        for (int i = from; i < to; i++) {
            Dtos.AvailableDay day = days.get(i);
            buttons.add(dayButton(day));
        }
        row.getChildren().setAll(buttons);

        LocalDate reference = from < days.size() ? days.get(from).date() : today;
        month.setText(Dates.monthYear(reference));
        boolean hasPrevious = page > 0;
        boolean hasNext = to < days.size();
        monthPrevious.setDisable(!hasPrevious);
        rowPrevious.setDisable(!hasPrevious);
        monthNext.setDisable(!hasNext);
        rowNext.setDisable(!hasNext);
    }

    private Button dayButton(Dtos.AvailableDay day) {
        Label name = new Label(Dates.dayLabel(day.date(), today));
        name.getStyleClass().add("day-name");
        Label number = new Label(String.valueOf(day.date().getDayOfMonth()));
        number.getStyleClass().add("day-number");
        VBox content = new VBox(6, name, number);
        content.setAlignment(Pos.CENTER);

        Button button = new Button();
        button.setGraphic(content);
        button.getStyleClass().add("day-button");
        button.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(button, Priority.ALWAYS);
        button.setDisable(day.freeSlots() <= 0);
        button.pseudoClassStateChanged(SELECTED, day.date().equals(selected));
        button.setAccessibleText(Dates.longDate(day.date()) + ", " + day.freeSlots() + " horarios libres");
        button.setOnAction(e -> {
            selected = day.date();
            render();
            onSelect.accept(day.date());
        });
        return button;
    }

    private int indexOf(LocalDate date) {
        for (int i = 0; i < days.size(); i++) {
            if (days.get(i).date().equals(date)) {
                return i;
            }
        }
        return -1;
    }

    private static Button chevron(String icon, String description) {
        Button b = new Button();
        b.setGraphic(Icons.of(icon, 22));
        b.getStyleClass().add("chevron");
        b.setAccessibleText(description);
        b.setFocusTraversable(false);
        return b;
    }
}
