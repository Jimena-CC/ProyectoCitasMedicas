package com.medicitas.api.appointment;

import com.medicitas.api.schedule.Slot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Traza de cada evento de una cita. {@code userId} es nulo cuando actúa el paciente desde el kiosko.
 */
@Entity
@Table(name = "appointment_history")
public class AppointmentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @Enumerated(EnumType.STRING)
    @Column(name = "event", nullable = false, length = 15)
    private AppointmentEvent event;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 12)
    private AppointmentStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 12)
    private AppointmentStatus newStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_slot_id")
    private Slot previousSlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_slot_id")
    private Slot newSlot;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "detail", length = 250)
    private String detail;

    @Column(name = "event_at", nullable = false)
    private Instant eventAt;

    protected AppointmentHistory() {
    }

    public static AppointmentHistory record(Appointment appointment, AppointmentEvent event,
                                            AppointmentStatus previousStatus, AppointmentStatus newStatus,
                                            Slot previousSlot, Slot newSlot, String detail, Instant eventAt) {
        AppointmentHistory history = new AppointmentHistory();
        history.appointment = appointment;
        history.event = event;
        history.previousStatus = previousStatus;
        history.newStatus = newStatus;
        history.previousSlot = previousSlot;
        history.newSlot = newSlot;
        history.detail = detail;
        history.eventAt = eventAt;
        return history;
    }

    public Long getId() {
        return id;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public AppointmentEvent getEvent() {
        return event;
    }

    public AppointmentStatus getPreviousStatus() {
        return previousStatus;
    }

    public AppointmentStatus getNewStatus() {
        return newStatus;
    }

    public Slot getPreviousSlot() {
        return previousSlot;
    }

    public Slot getNewSlot() {
        return newSlot;
    }

    public Long getUserId() {
        return userId;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getEventAt() {
        return eventAt;
    }
}
