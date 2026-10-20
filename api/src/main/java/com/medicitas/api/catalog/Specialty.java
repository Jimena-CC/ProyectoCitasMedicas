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

    // Issue #46: edición desde el panel de administración

    /**
     * Actualiza los datos editables de la especialidad.
     *
     * @param name               nombre único de la especialidad
     * @param description        descripción visible en el kiosko, puede ser nula
     * @param appointmentMinutes duración de cada cita en minutos
     */
    public void update(String name, String description, short appointmentMinutes) {
        this.name = name;
        this.description = description;
        this.appointmentMinutes = appointmentMinutes;
    }

    /**
     * Activa o desactiva la especialidad; una especialidad inactiva no se ofrece en el kiosko.
     *
     * @param active {@code true} para ofrecerla
     */
    public void setActive(boolean active) {
        this.active = active;
    }
}
