package com.medicitas.api.appointment;

import com.medicitas.api.patient.Patient;
import com.medicitas.api.patient.PatientInsurance;
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
import java.util.UUID;

@Entity
@Table(name = "appointment")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 12)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id", nullable = false)
    private Slot slot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_insurance_id")
    private PatientInsurance patientInsurance;

    @Column(name = "visit_reason", nullable = false, length = 250)
    private String visitReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private AppointmentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_channel", nullable = false, length = 10)
    private BookingChannel bookingChannel;

    /** Clave enviada por el cliente en la cabecera Idempotency-Key (RNF-07). */
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;

    @Column(name = "reschedule_count", nullable = false)
    private Short rescheduleCount = 0;

    @Column(name = "booked_at", nullable = false)
    private Instant bookedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Appointment() {
    }

    public Appointment(String code, Patient patient, Slot slot, PatientInsurance patientInsurance, String visitReason,
                       BookingChannel bookingChannel, UUID idempotencyKey, Instant bookedAt) {
        this.code = code;
        this.patient = patient;
        this.slot = slot;
        this.patientInsurance = patientInsurance;
        this.visitReason = visitReason;
        this.bookingChannel = bookingChannel;
        this.idempotencyKey = idempotencyKey;
        this.status = AppointmentStatus.BOOKED;
        this.bookedAt = bookedAt;
        this.updatedAt = bookedAt;
    }

    public void reschedule(Slot newSlot, Instant now) {
        this.slot = newSlot;
        this.rescheduleCount = (short) (rescheduleCount + 1);
        this.updatedAt = now;
    }

    public void cancel(Instant now) {
        this.status = AppointmentStatus.CANCELLED;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public Patient getPatient() {
        return patient;
    }

    public Slot getSlot() {
        return slot;
    }

    public PatientInsurance getPatientInsurance() {
        return patientInsurance;
    }

    public String getVisitReason() {
        return visitReason;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public BookingChannel getBookingChannel() {
        return bookingChannel;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public Short getRescheduleCount() {
        return rescheduleCount;
    }

    public Instant getBookedAt() {
        return bookedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
