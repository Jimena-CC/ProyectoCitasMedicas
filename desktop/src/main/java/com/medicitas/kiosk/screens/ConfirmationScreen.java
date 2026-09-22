package com.medicitas.kiosk.screens;

import com.medicitas.kiosk.api.Dtos;
import com.medicitas.kiosk.ui.Async;
import com.medicitas.kiosk.ui.Icons;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Paso 5: revisión, motivo de consulta y reserva (RF-06 a RF-08, RF-11). Muestra el código al confirmar. */
public class ConfirmationScreen extends Screen {

    private final StackPane container = new StackPane();
    private final VBox reviewRows = new VBox();
    private final TextArea visitReason = new TextArea();
    private final Label counter = label("0/250", "muted");
    private final Label visitReasonError = label("", "field-error");
    private Node review;

    public ConfirmationScreen(KioskContext ctx) {
        super(ctx);
    }

    @Override
    protected Node build() {
        visitReason.getStyleClass().add("kiosk-area");
        visitReason.setId("visitReason");
        visitReason.setWrapText(true);
        visitReason.setPromptText("Por ejemplo: control de presión arterial, dolor de rodilla al caminar…");
        visitReason.textProperty().addListener((o, a, text) -> {
            if (text.length() > 250) {
                visitReason.setText(text.substring(0, 250));
                return;
            }
            counter.setText(text.length() + "/250");
            visitReasonError.setText("");
            ctx.session().setVisitReason(text);
            ctx.updateNavigation();
        });

        HBox reasonFooter = new HBox(visitReasonError, new javafx.scene.layout.Region(), counter);
        javafx.scene.layout.HBox.setHgrow(reasonFooter.getChildren().get(1), javafx.scene.layout.Priority.ALWAYS);

        VBox body = card(
                title("Revisa y confirma tu cita"),
                subtitle("Verifica los datos antes de confirmar. Recibirás la confirmación por correo."),
                reviewRows,
                label("Motivo de consulta", "section-label"), visitReason, reasonFooter);
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("plain");
        review = scroll;
        container.getChildren().setAll(review);
        return container;
    }

    @Override
    public void onShow() {
        BookingSession s = ctx.session();
        if (s.getConfirmedAppointment() != null) {
            container.getChildren().setAll(success(s.getConfirmedAppointment()));
            return;
        }
        renderReview();
        visitReason.setText(s.getVisitReason() == null ? "" : s.getVisitReason());
        visitReasonError.setText("");
        container.getChildren().setAll(review);
    }

    private void renderReview() {
        BookingSession s = ctx.session();
        Dtos.Patient p = s.getPatient();
        Dtos.Slot c = s.getSlot();
        reviewRows.getChildren().setAll(
                row("Paciente", p.fullName(), null),
                row("Especialidad", s.getSpecialty().name(), null),
                row("Sede", s.getLocation().name(), s.getLocation().address()),
                row("Médico", c.doctor() == null ? "Por asignar" : c.doctor().fullName(),
                        c.doctor() == null ? null : "CMP " + c.doctor().license()),
                row("Fecha y hora", Dates.longDate(s.getSlotDate()) + ", " + Dates.range(c.startTime(), c.endTime()), null),
                row("Consultorio", c.room() == null ? "Se indicará en admisión" : c.room(), null),
                row("Cobertura", p.insurance() == null ? "Atención particular" : p.insurance().description(), null));
    }

    private Node success(Dtos.Appointment appointment) {
        boolean rescheduled = ctx.session().getMode() == BookingSession.Mode.RESCHEDULE;
        StackPane badge = new StackPane(Icons.of("check", 34, "icon-success"));
        badge.getStyleClass().add("success-badge");

        Label code = label(appointment.code(), "code-big");
        VBox codeBlock = new VBox(2, label("Código de cita", "section-label"), code);

        HBox header = new HBox(22, badge, new VBox(6,
                title(rescheduled ? "Reprogramamos tu cita" : "¡Tu cita está reservada!"),
                subtitle("Presenta este código en admisión el día de tu cita.")));
        header.setAlignment(Pos.CENTER_LEFT);

        VBox rows = new VBox(
                row("Médico", appointment.doctor(), null),
                row("Especialidad", appointment.specialty(), null),
                row("Sede", appointment.location(), appointment.room()),
                row("Fecha", Dates.longDate(appointment.date()), null),
                row("Hora", Dates.range(appointment.startTime(), appointment.endTime()), null),
                row("Cobertura", appointment.coverage() == null ? "Atención particular" : appointment.coverage(), null));

        Dtos.Patient p = ctx.session().getPatient();
        String email = p == null || p.email() == null ? "tu correo" : p.email();
        Label notice = label("Te enviamos la confirmación a tu correo " + email
                + " y un recordatorio 24 horas antes.", "banner-text");
        notice.setWrapText(true);
        HBox info = new HBox(12, Icons.of("mail", 20), notice);
        info.getStyleClass().addAll("banner", "info");

        Button myAppointments = new Button("Ver mis citas");
        myAppointments.getStyleClass().add("link-button");
        myAppointments.setOnAction(e -> ctx.showMyAppointments());

        VBox card = card(header, codeBlock, rows, info, myAppointments);
        ScrollPane scroll = new ScrollPane(card);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("plain");
        return scroll;
    }

    private static HBox row(String key, String value, String detail) {
        VBox values = new VBox(2, label(value, "review-value"));
        if (detail != null && !detail.isBlank()) {
            Label d = label(detail, "muted");
            if (detail.startsWith("CMP")) {
                d.getStyleClass().add("mono");
            }
            d.setStyle("-fx-font-size: 14px;");
            values.getChildren().add(d);
        }
        HBox h = new HBox(label(key, "review-key"), values);
        h.getStyleClass().add("review-row");
        return h;
    }

    private boolean confirmed() {
        return ctx.session().getConfirmedAppointment() != null;
    }

    @Override
    public String nextText() {
        return confirmed() ? "Finalizar" : "Confirmar reserva";
    }

    @Override
    public boolean showBack() {
        return !confirmed();
    }

    @Override
    public boolean nextEnabled() {
        return confirmed() || !visitReason.getText().isBlank();
    }

    @Override
    public void onNext(Button button) {
        if (confirmed()) {
            ctx.endVisit();
            return;
        }
        var problem = Validations.visitReason(visitReason.getText());
        if (problem.isPresent()) {
            visitReasonError.setText(problem.get());
            visitReason.requestFocus();
            return;
        }
        book(button);
    }

    private void book(Button button) {
        BookingSession s = ctx.session();
        ctx.hideNotice();
        Dtos.Patient p = s.getPatient();
        Dtos.NewAppointment body = new Dtos.NewAppointment(p.id(), s.getSlot().slotId(),
                p.insurance() == null ? null : p.insurance().patientInsuranceId(), visitReason.getText().trim());
        // La clave se genera una vez por intento y se reutiliza si hay que reintentar (RNF-07).
        Async.execute(ctx.api().bookAppointment(body, s.bookingKey()), button,
                appointment -> {
                    s.bookingConfirmed(appointment);
                    onShow();
                    ctx.updateNavigation();
                },
                error -> {
                    if (error.isScheduleConflict()) {
                        ctx.showError(error, null);
                        s.scheduleConflict();
                        ctx.showStep(BookingSession.Step.DATE_AND_TIME);
                    } else if (error.isNoConnection()) {
                        ctx.showError(error, () -> book(button));
                    } else {
                        ctx.showError(error, null);
                    }
                });
    }
}
