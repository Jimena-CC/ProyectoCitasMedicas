package com.medicitas.api.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "doctor")
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** Colegio Médico del Perú (CMP). */
    @Column(name = "license", nullable = false, unique = true, length = 10)
    private String license;

    /** Registro Nacional de Especialista (RNE). */
    @Column(name = "specialist_registry", length = 10)
    private String specialistRegistry;

    @Column(name = "first_names", nullable = false, length = 100)
    private String firstNames;

    @Column(name = "last_names", nullable = false, length = 100)
    private String lastNames;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "specialty_id", nullable = false)
    private Specialty specialty;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Doctor() {
    }

    public Doctor(String license, String specialistRegistry, String firstNames, String lastNames, Specialty specialty) {
        this.license = license;
        this.specialistRegistry = specialistRegistry;
        this.firstNames = firstNames;
        this.lastNames = lastNames;
        this.specialty = specialty;
    }

    public String getFullName() {
        return firstNames + " " + lastNames;
    }

    public Long getId() {
        return id;
    }

    public String getLicense() {
        return license;
    }

    public String getSpecialistRegistry() {
        return specialistRegistry;
    }

    public String getFirstNames() {
        return firstNames;
    }

    public String getLastNames() {
        return lastNames;
    }

    public Specialty getSpecialty() {
        return specialty;
    }

    public boolean isActive() {
        return active;
    }
}
