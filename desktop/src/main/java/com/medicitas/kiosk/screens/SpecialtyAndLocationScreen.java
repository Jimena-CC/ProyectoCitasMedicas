package com.medicitas.kiosk.screens;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.theme.Styles;
import com.medicitas.kiosk.api.Dtos;
import com.medicitas.kiosk.ui.Async;
import com.medicitas.kiosk.ui.Icons;
import com.medicitas.kiosk.ui.SummaryCard;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Paso 3: elegir sede y especialidad activa en esa sede (RF-04). */
public class SpecialtyAndLocationScreen extends Screen {

    private final ToggleGroup locations = new ToggleGroup();
    private final HBox segmented = new HBox();
    private final Label address = label("", "location-address");
    private final CustomTextField search = new CustomTextField();
    private final ObservableList<Dtos.Specialty> specialties = FXCollections.observableArrayList();
    private final FilteredList<Dtos.Specialty> filtered = new FilteredList<>(specialties);
    private final ListView<Dtos.Specialty> list = new ListView<>(filtered);
    private final SummaryCard summary = new SummaryCard("Seleccionado actualmente:", "hospital-circle");
    private final Map<Long, List<Dtos.Specialty>> cache = new HashMap<>();
    private List<Dtos.Location> loadedLocations;
    private boolean rendering;

    public SpecialtyAndLocationScreen(KioskContext ctx) {
        super(ctx);
    }

    @Override
    protected Node build() {
        segmented.getStyleClass().add("segmented");
        locations.selectedToggleProperty().addListener((obs, before, now) -> {
            if (now == null) {
                if (before != null) {
                    before.setSelected(true);
                }
                return;
            }
            if (!rendering) {
                onLocationChosen((Dtos.Location) now.getUserData());
            }
        });

        search.setPromptText("Buscar especialidad, por ejemplo: cardiología");
        search.setLeft(Icons.of("search", 20));
        search.getStyleClass().add("kiosk-field");
        search.setId("specialtySearch");
        search.textProperty().addListener((o, a, text) -> {
            String q = normalize(text);
            filtered.setPredicate(e -> q.isEmpty() || normalize(e.name()).contains(q));
        });

        list.getStyleClass().add("specialty-list");
        list.setPlaceholder(label("No encontramos especialidades con ese nombre.", "muted"));
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(Dtos.Specialty e, boolean empty) {
                super.updateItem(e, empty);
                if (empty || e == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label name = label(e.name(), "review-value");
                Label desc = label(e.description() == null ? "" : e.description(), "muted");
                desc.setStyle("-fx-font-size: 14px;");
                desc.setWrapText(true);
                VBox box = new VBox(2, name, desc);
                box.setMaxWidth(640);
                setGraphic(box);
                setText(null);
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((o, a, e) -> {
            if (e != null && !rendering) {
                ctx.session().selectSpecialty(e);
                updateSummary();
                ctx.updateNavigation();
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);
        list.setPrefHeight(320);

        VBox locationColumn = new VBox(12, label("Sede", "section-label"), segmented, address);
        VBox card = card(
                title("Especialidad y sede"),
                subtitle("Elige dónde quieres atenderte y la especialidad que necesitas."),
                locationColumn,
                label("Especialidad", "section-label"), search, list);
        VBox.setVgrow(card, Priority.ALWAYS);
        summary.setEmpty("Elige una sede y una especialidad");
        VBox root = new VBox(18, card, summary);
        VBox.setVgrow(card, Priority.ALWAYS);
        return root;
    }

    @Override
    public void onShow() {
        if (loadedLocations == null) {
            loadLocations();
        } else {
            renderLocations();
        }
    }

    private void loadLocations() {
        Async.execute(ctx.api().locations(), null,
                result -> {
                    loadedLocations = result;
                    renderLocations();
                },
                error -> ctx.showError(error, this::loadLocations));
    }

    private void renderLocations() {
        rendering = true;
        segmented.getChildren().clear();
        locations.getToggles().clear();
        for (int i = 0; i < loadedLocations.size(); i++) {
            Dtos.Location location = loadedLocations.get(i);
            ToggleButton t = new ToggleButton(location.name());
            t.setUserData(location);
            t.setToggleGroup(locations);
            t.setFocusTraversable(false);
            t.getStyleClass().add(loadedLocations.size() == 1 ? Styles.ROUNDED
                    : i == 0 ? Styles.LEFT_PILL : i == loadedLocations.size() - 1 ? Styles.RIGHT_PILL : Styles.CENTER_PILL);
            segmented.getChildren().add(t);
        }
        rendering = false;

        Dtos.Location current = ctx.session().getLocation();
        Dtos.Location chosen = current == null ? loadedLocations.get(0) : current;
        locations.getToggles().stream()
                .filter(t -> ((Dtos.Location) t.getUserData()).id().equals(chosen.id()))
                .findFirst()
                .ifPresent(t -> {
                    if (t.isSelected()) {
                        onLocationChosen(chosen);
                    } else {
                        t.setSelected(true);
                    }
                });
    }

    private void onLocationChosen(Dtos.Location location) {
        ctx.session().selectLocation(location);
        address.setText(location.address() + " · " + location.district());
        updateSummary();
        ctx.updateNavigation();
        List<Dtos.Specialty> cached = cache.get(location.id());
        if (cached != null) {
            renderSpecialties(cached);
            return;
        }
        specialties.clear();
        list.setPlaceholder(label("Cargando especialidades…", "muted"));
        Async.execute(ctx.api().specialties(location.id()), null,
                result -> {
                    cache.put(location.id(), result);
                    if (ctx.session().getLocation() != null && ctx.session().getLocation().id().equals(location.id())) {
                        renderSpecialties(result);
                    }
                },
                error -> ctx.showError(error, () -> onLocationChosen(location)));
    }

    private void renderSpecialties(List<Dtos.Specialty> result) {
        rendering = true;
        specialties.setAll(result);
        list.setPlaceholder(label("No encontramos especialidades con ese nombre.", "muted"));
        Dtos.Specialty current = ctx.session().getSpecialty();
        list.getSelectionModel().clearSelection();
        if (current != null) {
            result.stream().filter(e -> e.id().equals(current.id())).findFirst().ifPresent(e -> {
                list.getSelectionModel().select(e);
                list.scrollTo(e);
            });
        }
        rendering = false;
    }

    private void updateSummary() {
        BookingSession s = ctx.session();
        if (s.getSpecialty() != null && s.getLocation() != null) {
            summary.setValue(s.getSpecialty().name() + "  ·  " + s.getLocation().name());
        } else {
            summary.setEmpty("Elige una especialidad en " + (s.getLocation() == null ? "la sede" : s.getLocation().name()));
        }
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text.trim().toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    @Override
    public boolean nextEnabled() {
        return ctx.session().isStepComplete(BookingSession.Step.SPECIALTY_AND_LOCATION);
    }

    @Override
    public void onNext(Button button) {
        ctx.advance();
    }
}
