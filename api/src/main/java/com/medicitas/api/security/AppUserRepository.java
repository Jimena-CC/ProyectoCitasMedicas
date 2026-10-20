package com.medicitas.api.security;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

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

    boolean existsByUsernameIgnoreCase(String username);
}
