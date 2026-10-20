package com.medicitas.api.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SpecialtyRepository extends JpaRepository<Specialty, Long> {

    List<Specialty> findByActiveTrueOrderByNameAsc();

    @Query("""
            select ls.specialty from LocationSpecialty ls
            where ls.location.id = :locationId and ls.active = true and ls.specialty.active = true
            order by ls.specialty.name""")
    List<Specialty> findActiveByLocation(@Param("locationId") Long locationId);

    // Issue #46: administración del catálogo

    /**
     * Todas las especialidades, activas e inactivas, ordenadas por nombre.
     *
     * @return las especialidades registradas
     */
    List<Specialty> findAllByOrderByNameAsc();

    /**
     * Indica si ya existe una especialidad con ese nombre, sin distinguir mayúsculas.
     *
     * @param name nombre a comprobar
     * @return {@code true} si el nombre ya está en uso
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Indica si otra especialidad (distinta de la indicada) ya usa ese nombre, sin distinguir mayúsculas.
     *
     * @param name nombre a comprobar
     * @param id   especialidad que se está editando
     * @return {@code true} si otra especialidad ya usa el nombre
     */
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
