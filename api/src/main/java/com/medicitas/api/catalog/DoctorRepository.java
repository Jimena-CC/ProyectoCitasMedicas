package com.medicitas.api.catalog;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    // Issue #46: administración del catálogo

    /**
     * Todos los médicos, activos e inactivos, con su especialidad cargada y ordenados por apellidos.
     *
     * @return los médicos registrados
     */
    @Query("select d from Doctor d join fetch d.specialty order by d.lastNames, d.firstNames")
    List<Doctor> findAllWithSpecialty();

    /**
     * Busca un médico por id con su especialidad cargada.
     *
     * @param id identificador del médico
     * @return el médico, si existe
     */
    @EntityGraph(attributePaths = "specialty")
    Optional<Doctor> findWithSpecialtyById(Long id);

    /**
     * Indica si ya existe un médico con ese número de colegiatura, sin distinguir mayúsculas.
     *
     * @param license número de colegiatura (CMP)
     * @return {@code true} si la colegiatura ya está registrada
     */
    boolean existsByLicenseIgnoreCase(String license);

    /**
     * Indica si otro médico (distinto del indicado) ya tiene ese número de colegiatura.
     *
     * @param license número de colegiatura (CMP)
     * @param id      médico que se está editando
     * @return {@code true} si otro médico ya usa la colegiatura
     */
    boolean existsByLicenseIgnoreCaseAndIdNot(String license, Long id);
}
