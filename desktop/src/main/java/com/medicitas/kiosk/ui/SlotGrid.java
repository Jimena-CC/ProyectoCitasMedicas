package com.medicitas.kiosk.ui;

import com.medicitas.kiosk.api.Dtos;
import com.medicitas.kiosk.screens.Dates;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Horarios libres de un día agrupados por médico. Muestra primero {@value #VISIBLE} horarios y
 * el resto detrás de "Ver más horarios".
 */
public class SlotGrid extends VBox {

    public static final int VISIBLE = 14;

    private final ToggleGroup group = new ToggleGroup();
    private Consumer<Dtos.Slot> onSelect = s -> { };
    private Dtos.AvailableDay day;
    private Long selectedId;
    private boolean expanded;

    public SlotGrid() {
        setSpacing(18);
    }

    public void show(Dtos.AvailableDay newDay, Long selectedSlotId) {
        if (day == null || newDay == null || !day.date().equals(newDay.date())) {
            expanded = false;
        }
        this.day = newDay;
        this.selectedId = selectedSlotId;
        render();
    }

    public void setOnSlotSelected(Consumer<Dtos.Slot> action) {
        this.onSelect = action;
    }

    private void render() {
        getChildren().clear();
        group.getToggles().clear();
        List<Dtos.Slot> slots = day == null || day.slots() == null ? List.of() : day.slots();
        if (slots.isEmpty()) {
            Label empty = new Label("No hay horarios libres para este día. Elige otra fecha.");
            empty.getStyleClass().add("muted");
            getChildren().add(empty);
            return;
        }

        int limit = expanded ? slots.size() : Math.min(VISIBLE, slots.size());
        if (!expanded && selectedId != null) {
            for (int i = VISIBLE; i < slots.size(); i++) {
                if (slots.get(i).slotId().equals(selectedId)) {
                    limit = slots.size();
                    expanded = true;
                    break;
                }
            }
        }

        Map<Long, VBox> groups = new LinkedHashMap<>();
        Map<Long, FlowPane> chips = new LinkedHashMap<>();
        for (int i = 0; i < limit; i++) {
            Dtos.Slot slot = slots.get(i);
            Dtos.DoctorSummary doctor = slot.doctor();
            Long doctorId = doctor == null ? -1L : doctor.id();
            FlowPane flow = chips.computeIfAbsent(doctorId, id -> {
                FlowPane f = new FlowPane();
                f.getStyleClass().add("slot-grid");
                VBox block = new VBox(10, doctorHeader(doctor), f);
                groups.put(id, block);
                return f;
            });
            flow.getChildren().add(chip(slot));
        }
        getChildren().addAll(groups.values());

        int remaining = slots.size() - limit;
        if (remaining > 0) {
            Button showMore = new Button("Ver más horarios");
            showMore.getStyleClass().add("link-button");
            showMore.setGraphic(Icons.of("nav-arrow-down", 20));
            showMore.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
            showMore.setOnAction(e -> {
                expanded = true;
                render();
            });
            Label count = new Label("(" + remaining + (remaining == 1 ? " disponible)" : " disponibles)"));
            HBox row = new HBox(12, showMore, count);
            row.setAlignment(Pos.CENTER_LEFT);
            getChildren().add(row);
        }
    }

    private HBox doctorHeader(Dtos.DoctorSummary doctor) {
        Label name = new Label(doctor == null ? "Médico por asignar" : doctor.fullName());
        name.getStyleClass().add("doctor-name");
        HBox box = new HBox(name);
        box.getStyleClass().add("doctor-header");
        if (doctor != null && doctor.license() != null) {
            Label license = new Label("CMP " + doctor.license());
            license.getStyleClass().addAll("doctor-license", "mono");
            box.getChildren().add(license);
        }
        return box;
    }

    private ToggleButton chip(Dtos.Slot slot) {
        ToggleButton chip = new ToggleButton(Dates.time(slot.startTime()));
        chip.getStyleClass().add("slot-chip");
        chip.setToggleGroup(group);
        chip.setUserData(slot);
        chip.setSelected(slot.slotId().equals(selectedId));
        chip.setAccessibleText("Horario " + Dates.range(slot.startTime(), slot.endTime()));
        chip.setOnAction(e -> {
            if (!chip.isSelected()) {
                chip.setSelected(true);
                return;
            }
            selectedId = slot.slotId();
            onSelect.accept(slot);
        });
        return chip;
    }
}
