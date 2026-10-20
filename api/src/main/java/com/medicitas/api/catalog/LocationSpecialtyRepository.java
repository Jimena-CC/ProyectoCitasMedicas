package com.medicitas.api.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LocationSpecialtyRepository extends JpaRepository<LocationSpecialty, LocationSpecialty.Id> {

    // Issue #46: administración del catálogo y disponibilidad del kiosko

    /**
     * Todas las relaciones sede-especialidad con ambas entidades cargadas.
     *
     * @return las relaciones registradas, activas e inactivas
     */
    @Query("select ls from LocationSpecialty ls join fetch ls.location join fetch ls.specialty")
    List<LocationSpecialty> findAllWithLocationAndSpecialty();

    /**
     * Relaciones de una especialidad con sus sedes cargadas.
     *
     * @param specialtyId id de la especialidad
     * @return las relaciones de la especialidad, activas e inactivas
     */
    @Query("select ls from LocationSpecialty ls join fetch ls.location where ls.specialty.id = :specialtyId")
    List<LocationSpecialty> findBySpecialtyId(@Param("specialtyId") Long specialtyId);

    /**
     * Indica si la sede atiende la especialidad (relación activa).
     *
     * @param locationId  id de la sede
     * @param specialtyId id de la especialidad
     * @return {@code true} si la especialidad se atiende en esa sede
     */
    @Query("""
            select count(ls) > 0 from LocationSpecialty ls
            where ls.location.id = :locationId and ls.specialty.id = :specialtyId and ls.active = true""")
    boolean isOffered(@Param("locationId") Long locationId, @Param("specialtyId") Long specialtyId);
}
