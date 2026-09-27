package com.medicitas.api.schedule;

import com.medicitas.api.catalog.Doctor;
import com.medicitas.api.catalog.Location;
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
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Espacio de atención de un médico en una sede. {@code version} se incrementa en cada cambio de estado
 * mediante una actualización condicional, lo que impide que dos reservas tomen el mismo cupo (RF-07).
 */
@Entity
@Table(name = "slot",
        uniqueConstraints = @UniqueConstraint(name = "uk_slot_doctor_schedule", columnNames = {"doctor_id", "date", "start_time"}))
public class Slot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "room", length = 20)
    private String room;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private SlotStatus status = SlotStatus.FREE;

    @Column(name = "version", nullable = false)
    private Integer version = 0;

    protected Slot() {
    }

    public Slot(Doctor doctor, Location location, LocalDate date, LocalTime startTime, LocalTime endTime, String room) {
        this.doctor = doctor;
        this.location = location;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.room = room;
    }

    public LocalDateTime getStart() {
        return date.atTime(startTime);
    }

    public Instant startAt(ZoneId zone) {
        return getStart().atZone(zone).toInstant();
    }

    public Long getId() {
        return id;
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public Location getLocation() {
        return location;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public String getRoom() {
        return room;
    }

    public SlotStatus getStatus() {
        return status;
    }

    public void setStatus(SlotStatus status) {
        this.status = status;
    }

    public Integer getVersion() {
        return version;
    }
}
