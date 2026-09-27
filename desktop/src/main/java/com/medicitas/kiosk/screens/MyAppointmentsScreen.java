package com.medicitas.kiosk.screens;

import com.medicitas.kiosk.api.ApiException;
import com.medicitas.kiosk.api.Dtos;
import com.medicitas.kiosk.ui.Async;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Citas vigentes del paciente con opciones para reprogramar (RF-09) o anular (RF-10). */
public class MyAppointmentsScreen extends Screen {

    private static final String[] SHORT_MONTHS = {"ENE", "FEB", "MAR", "ABR", "MAY", "JUN", "JUL", "AGO",
            "SEP", "OCT", "NOV", "DIC"};

    private final VBox list = new VBox(14);
    private final Label greeting = subtitle("");

    public MyAppointmentsScreen(KioskContext ctx) {
        super(ctx);
    }

    @Override
    protected Node build() {
        Button newOne = new Button("Reservar una nueva cita");
        newOne.getStyleClass().add("link-button");
        newOne.setOnAction(e -> bookNew());
        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        HBox header = new HBox(new VBox(6, title("Mis citas"), greeting), space, newOne);
        header.setAlignment(Pos.TOP_LEFT);

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("plain");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox card = card(header, scroll);
        VBox.setVgrow(card, Priority.ALWAYS);
        return card;
    }

    @Override
    public void onShow() {
        Dtos.Patient p = ctx.session().getPatient();
        greeting.setText(p.firstName() + ", estas son tus citas reservadas en la clínica.");
        load();
    }

    private void load() {
        list.getChildren().setAll(label("Buscando tus citas…", "muted"));
        Async.execute(ctx.api().upcomingAppointments(ctx.session().getPatient().id()), null,
                this::render,
                error -> {
                    list.getChildren().clear();
                    ctx.showError(error, this::load);
                });
    }

    private void render(List<Dtos.Appointment> appointments) {
        list.getChildren().clear();
        if (appointments.isEmpty()) {
            Button book = new Button("Reservar una cita");
            book.getStyleClass().addAll("kiosk-button", "accent");
            book.setOnAction(e -> bookNew());
            VBox empty = new VBox(14, label("No tienes citas reservadas por ahora.", "review-value"), book);
            list.getChildren().add(empty);
            return;
        }
        appointments.forEach(a -> list.getChildren().add(appointmentCard(a)));
    }

    private Node appointmentCard(Dtos.Appointment appointment) {
        VBox date = new VBox(0,
                label(String.valueOf(appointment.date().getDayOfMonth()), "appointment-date-day"),
                label(SHORT_MONTHS[appointment.date().getMonthValue() - 1], "appointment-date-month"));
        date.getStyleClass().add("appointment-date-block");

        Label specialty = label(appointment.specialty(), "review-value");
        specialty.setStyle("-fx-font-size: 19px; -fx-font-weight: 600;");
        Label when = label(Dates.longDate(appointment.date()) + " · "
                + Dates.range(appointment.startTime(), appointment.endTime()), "review-value");
        Label where = label(appointment.doctor() + " · " + appointment.location()
                + (appointment.room() == null ? "" : " · " + appointment.room()), "muted");
        where.setWrapText(true);
        where.setMaxWidth(Double.MAX_VALUE);
        Label code = label(appointment.code(), "mono", "muted");
        VBox data = new VBox(4, specialty, when, where, code);
        HBox.setHgrow(data, Priority.ALWAYS);

        Button reschedule = new Button("Reprogramar");
        reschedule.getStyleClass().addAll("kiosk-button", "button-outlined");
        reschedule.setOnAction(e -> reschedule(appointment, reschedule));
        reschedule.setMinWidth(Region.USE_PREF_SIZE);
        Button cancel = new Button("Anular");
        cancel.getStyleClass().addAll("kiosk-button", "button-outlined", "danger");
        cancel.setOnAction(e -> confirmCancellation(appointment));

        cancel.setMinWidth(Region.USE_PREF_SIZE);
        HBox actions = new HBox(12, reschedule, cancel);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setMinWidth(Region.USE_PREF_SIZE);
        HBox card = new HBox(date, data, actions);
        card.getStyleClass().add("appointment-card");
        return card;
    }

    /** Ubica sede y especialidad de la cita en los catálogos para consultar la misma disponibilidad. */
    private void reschedule(Dtos.Appointment appointment, Button button) {
        ctx.hideNotice();
        CompletableFuture<Object[]> preparation = ctx.api().locations().thenCompose(locations -> {
            Dtos.Location location = locations.stream().filter(l -> l.name().equals(appointment.location())).findFirst()
                    .orElseThrow(() -> new ApiException(404, "RESOURCE_NOT_FOUND",
                            "No pudimos preparar la reprogramación. Acércate a Admisión para ayudarte.", List.of()));
            return ctx.api().specialties(location.id()).thenApply(specialties -> {
                Dtos.Specialty specialty = specialties.stream()
                        .filter(e -> e.name().equals(appointment.specialty())).findFirst()
                        .orElseThrow(() -> new ApiException(404, "RESOURCE_NOT_FOUND",
                                "La especialidad de esta cita ya no tiene horarios en el kiosko. Acércate a Admisión.",
                                List.of()));
                return new Object[]{location, specialty};
            });
        });
        Async.execute(preparation, button,
                data -> {
                    ctx.session().startReschedule(appointment, (Dtos.Location) data[0], (Dtos.Specialty) data[1]);
                    ctx.showStep(BookingSession.Step.DATE_AND_TIME);
                },
                error -> ctx.showError(error, error.isNoConnection() ? () -> reschedule(appointment, button) : null));
    }

    private void confirmCancellation(Dtos.Appointment appointment) {
        TextField reason = new TextField();
        reason.getStyleClass().add("kiosk-field");
        reason.setId("cancellationReason");
        reason.setPromptText("Motivo (opcional)");

        Button keep = new Button("Mantener cita");
        keep.getStyleClass().addAll("kiosk-button", "button-outlined");
        keep.setOnAction(e -> ctx.closeDialog());
        Button confirm = new Button("Sí, anular");
        confirm.getStyleClass().addAll("kiosk-button", "danger");
        confirm.setOnAction(e -> cancel(appointment, reason.getText(), confirm));

        HBox actions = new HBox(12, keep, confirm);
        actions.getStyleClass().add("actions");
        Label detail = subtitle(appointment.specialty() + " · " + Dates.longDate(appointment.date()) + ", "
                + Dates.time(appointment.startTime()) + ". El horario quedará libre para otro paciente.");
        VBox dialog = new VBox(title("¿Anular esta cita?"), detail, reason, actions);
        dialog.getStyleClass().add("dialog-card");
        ctx.showDialog(dialog);
    }

    private void cancel(Dtos.Appointment appointment, String reason, Button button) {
        String text = reason == null || reason.isBlank() ? "Anulada por el paciente desde el kiosko" : reason.trim();
        Async.execute(ctx.api().cancelAppointment(appointment.id(), text), button,
                cancelled -> {
                    ctx.closeDialog();
                    ctx.showInfo("Anulamos tu cita " + cancelled.code() + ". Te enviaremos el aviso por correo.");
                    load();
                },
                error -> {
                    ctx.closeDialog();
                    ctx.showError(error, error.isNoConnection() ? () -> cancel(appointment, reason, button) : null);
                });
    }

    private void bookNew() {
        ctx.session().newBooking();
        ctx.showStep(BookingSession.Step.SPECIALTY_AND_LOCATION);
    }

    @Override
    public boolean showNext() {
        return false;
    }

    @Override
    public void onBack() {
        ctx.showStep(BookingSession.Step.IDENTIFICATION);
    }

    @Override
    public void onNext(Button button) {
        // Sin acción principal: cada cita tiene sus propios botones.
    }
}
