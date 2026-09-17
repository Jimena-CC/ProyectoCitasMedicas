package com.medicitas.api.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/**
 * Especialidades que se atienden en cada sede (RF-04).
 */
@Entity
@Table(name = "location_specialty")
public class LocationSpecialty {

    @EmbeddedId
    private Id id;

    @MapsId("locationId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id")
    private Location location;

    @MapsId("specialtyId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "specialty_id")
    private Specialty specialty;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected LocationSpecialty() {
    }

    public LocationSpecialty(Location location, Specialty specialty) {
        this.id = new Id(location.getId(), specialty.getId());
        this.location = location;
        this.specialty = specialty;
    }

    public Location getLocation() {
        return location;
    }

    public Specialty getSpecialty() {
        return specialty;
    }

    public boolean isActive() {
        return active;
    }

    @Embeddable
    public static class Id implements Serializable {

        @Column(name = "location_id")
        private Long locationId;

        @Column(name = "specialty_id")
        private Long specialtyId;

        protected Id() {
        }

        public Id(Long locationId, Long specialtyId) {
            this.locationId = locationId;
            this.specialtyId = specialtyId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Id that)) {
                return false;
            }
            return Objects.equals(locationId, that.locationId) && Objects.equals(specialtyId, that.specialtyId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(locationId, specialtyId);
        }
    }
}
