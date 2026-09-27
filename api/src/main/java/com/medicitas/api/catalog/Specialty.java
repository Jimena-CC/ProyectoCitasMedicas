package com.medicitas.api.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "specialty")
public class Specialty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 120)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "appointment_minutes", nullable = false)
    private Short appointmentMinutes = 20;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Specialty() {
    }

    public Specialty(String name, String description, short appointmentMinutes) {
        this.name = name;
        this.description = description;
        this.appointmentMinutes = appointmentMinutes;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Short getAppointmentMinutes() {
        return appointmentMinutes;
    }

    public boolean isActive() {
        return active;
    }
}
