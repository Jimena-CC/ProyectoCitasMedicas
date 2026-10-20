package com.medicitas.api.security;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a los usuarios del personal ({@code app_user}).
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /**
     * Busca un usuario por su nombre sin distinguir mayúsculas; carga el médico vinculado.
     *
     * @param username nombre de usuario tal como se escribió en el formulario de ingreso
     * @return el usuario si existe
     */
    @EntityGraph(attributePaths = "doctor")
    Optional<AppUser> findByUsernameIgnoreCase(String username);

    /**
     * Busca un usuario por id cargando el médico vinculado.
     *
     * @param id identificador del usuario
     * @return el usuario si existe
     */
    @EntityGraph(attributePaths = "doctor")
    Optional<AppUser> findWithDoctorById(Long id);

    /**
     * Lista todos los usuarios ordenados por nombre, con su médico vinculado.
     *
     * @return los usuarios registrados
     */
    @EntityGraph(attributePaths = "doctor")
    List<AppUser> findAllByOrderByUsernameAsc();

    /**
     * Indica si ya existe un usuario con ese nombre, sin distinguir mayúsculas.
     *
     * @param username nombre de usuario a comprobar
     * @return {@code true} si el nombre ya está en uso
     */
    boolean existsByUsernameIgnoreCase(String username);

    // Issue #46: administración de usuarios

    /**
     * Busca el usuario vinculado a un médico; cada médico tiene como máximo un usuario.
     *
     * @param doctorId id del médico
     * @return el usuario vinculado, si existe
     */
    @Query("select u from AppUser u join fetch u.doctor d where d.id = :doctorId")
    Optional<AppUser> findByDoctorId(@Param("doctorId") Long doctorId);
}
