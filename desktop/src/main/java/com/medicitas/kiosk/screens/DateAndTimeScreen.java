package com.medicitas.kiosk.screens;

import com.medicitas.kiosk.api.Dtos;
import com.medicitas.kiosk.ui.Async;
import com.medicitas.kiosk.ui.DateStrip;
import com.medicitas.kiosk.ui.SlotGrid;
import com.medicitas.kiosk.ui.SummaryCard;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;

/** Paso 4: franja de 14 días y horarios libres por médico (RF-05). También sirve para reprogramar (RF-09). */
public class DateAndTimeScreen extends Screen {

    private static final int DAYS = 14;

    private final DateStrip strip = new DateStrip();
    private final SlotGrid slotGrid = new SlotGrid();
    private final SummaryCard summary = new SummaryCard("Seleccionado actualmente:", "clock");
    private final Label heading = title("Elige fecha y hora");
    private final Label context = subtitle("");
    private List<Dtos.AvailableDay> days = List.of();

    public DateAndTimeScreen(KioskContext ctx) {
        super(ctx);
    }

    @Override
    protected Node build() {
        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        VBox texts = new VBox(6, heading, context);
        HBox header = new HBox(16, texts, space, strip.monthSelector());
        header.setAlignment(Pos.TOP_LEFT);

        strip.setOnDateSelected(this::showDay);
        slotGrid.setOnSlotSelected(slot -> {
            ctx.session().selectSlot(strip.getSelected(), slot);
            updateSummary();
            ctx.updateNavigation();
        });

        ScrollPane scroll = new ScrollPane(slotGrid);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("plain");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox card = card(header, strip, scroll);
        VBox.setVgrow(card, Priority.ALWAYS);
        return new VBox(18, card, summary);
    }

    @Override
    public void onShow() {
        BookingSession s = ctx.session();
        boolean rescheduling = s.getMode() == BookingSession.Mode.RESCHEDULE;
        heading.setText(rescheduling ? "Elige tu nuevo horario" : "Elige fecha y hora");
        String base = s.getSpecialty().name() + " en " + s.getLocation().name();
        if (rescheduling && s.getAppointmentToReschedule() != null) {
            Dtos.Appointment a = s.getAppointmentToReschedule();
            context.setText(base + ". Tu cita actual es el " + Dates.longDate(a.date()).toLowerCase()
                    + " a las " + Dates.time(a.startTime()) + ".");
        } else {
            context.setText(base);
        }
        updateSummary();
        load();
    }

    private void load() {
        BookingSession s = ctx.session();
        LocalDate today = LocalDate.now();
        slotGrid.show(null, null);
        summary.setEmpty("Buscando horarios libres…");
        Async.execute(ctx.api().availability(s.getSpecialty().id(), s.getLocation().id(), today, today.plusDays(DAYS - 1)),
                null,
                result -> {
                    days = result.days() == null ? List.of() : result.days();
                    LocalDate chosen = chooseDate(s);
                    if (chosen != null) {
                        strip.select(chosen);
                    }
                    strip.setDays(days, today);
                    if (chosen == null) {
                        ctx.showInfo("No hay horarios libres en los próximos 14 días para " + s.getSpecialty().name()
                                + " en " + s.getLocation().name() + ". Prueba en otra sede.");
                        slotGrid.show(null, null);
                    } else {
                        showDay(chosen);
                    }
                    updateSummary();
                },
                error -> {
                    summary.setEmpty("No pudimos cargar los horarios");
                    ctx.showError(error, this::load);
                });
    }

    /** Conserva la fecha y el horario elegidos si siguen libres; si no, elige el primer día con cupos. */
    private LocalDate chooseDate(BookingSession s) {
        if (s.getSlot() != null && s.getSlotDate() != null) {
            boolean stillFree = days.stream()
                    .filter(d -> d.date().equals(s.getSlotDate()))
                    .flatMap(d -> d.slots().stream())
                    .anyMatch(c -> c.slotId().equals(s.getSlot().slotId()));
            if (stillFree) {
                return s.getSlotDate();
            }
            s.scheduleConflict();
        }
        return days.stream().filter(d -> d.freeSlots() > 0).map(Dtos.AvailableDay::date).findFirst().orElse(null);
    }

    private void showDay(LocalDate date) {
        Dtos.AvailableDay day = days.stream().filter(d -> d.date().equals(date)).findFirst().orElse(null);
        Dtos.Slot slot = ctx.session().getSlot();
        Long selected = slot != null && date.equals(ctx.session().getSlotDate()) ? slot.slotId() : null;
        slotGrid.show(day, selected);
    }

    private void updateSummary() {
        BookingSession s = ctx.session();
        Dtos.Slot slot = s.getSlot();
        if (slot == null) {
            summary.setEmpty("Toca un horario para seleccionarlo");
            return;
        }
        String doctor = slot.doctor() == null ? "" : "  ·  " + slot.doctor().fullName();
        summary.setValue(Dates.longDate(s.getSlotDate()) + ", " + Dates.range(slot.startTime(), slot.endTime()) + doctor);
    }

    @Override
    public boolean nextEnabled() {
        return ctx.session().getSlot() != null;
    }

    @Override
    public String nextText() {
        return ctx.session().getMode() == BookingSession.Mode.RESCHEDULE ? "Reprogramar cita" : "Siguiente";
    }

    @Override
    public void onBack() {
        if (ctx.session().getMode() == BookingSession.Mode.RESCHEDULE) {
            ctx.showMyAppointments();
        } else {
            ctx.goBack();
        }
    }

    @Override
    public void onNext(Button button) {
        BookingSession s = ctx.session();
        if (s.getMode() == BookingSession.Mode.BOOKING) {
            ctx.advance();
            return;
        }
        reschedule(button);
    }

    private void reschedule(Button button) {
        BookingSession s = ctx.session();
        ctx.hideNotice();
        Async.execute(ctx.api().rescheduleAppointment(s.getAppointmentToReschedule().id(), s.getSlot().slotId()), button,
                appointment -> {
                    s.bookingConfirmed(appointment);
                    ctx.advance();
                },
                error -> {
                    if (error.isScheduleConflict()) {
                        ctx.showError(error, null);
                        s.scheduleConflict();
                        load();
                    } else if ("APPOINTMENT_NOT_MODIFIABLE".equals(error.getCode())) {
                        ctx.showError(error, null);
                        ctx.showMyAppointments();
                    } else {
                        ctx.showError(error, () -> reschedule(button));
                    }
                });
    }
}
