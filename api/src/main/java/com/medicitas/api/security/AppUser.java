package com.medicitas.api.security;

import com.medicitas.api.catalog.Doctor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Usuario del personal de la clínica (tabla {@code app_user}).
 * La contraseña se guarda únicamente como hash BCrypt; un usuario con perfil {@link UserRole#DOCTOR}
 * queda vinculado a su ficha de {@link Doctor} para consultar solo su propia agenda.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    /** Hash BCrypt de la contraseña (60 caracteres; la columna admite 72). */
    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 15)
    private UserRole role;

    /** Médico asociado; solo tiene valor para el perfil {@link UserRole#DOCTOR}. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", unique = true)
    private Doctor doctor;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "last_access")
    private Instant lastAccess;

    protected AppUser() {
    }

    /**
     * Crea un usuario activo.
     *
     * @param username     nombre de usuario único, sin distinguir mayúsculas al iniciar sesión
     * @param passwordHash hash BCrypt de la contraseña, nunca la contraseña en claro
     * @param role         perfil del usuario
     * @param doctor       médico vinculado, obligatorio para {@link UserRole#DOCTOR} y nulo para el resto
     */
    public AppUser(String username, String passwordHash, UserRole role, Doctor doctor) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.doctor = doctor;
    }

    /**
     * Registra el momento del último inicio de sesión correcto.
     *
     * @param now instante del inicio de sesión
     */
    public void touchLastAccess(Instant now) {
        this.lastAccess = now;
    }

    /**
     * Activa o desactiva el usuario; un usuario inactivo no puede iniciar sesión.
     *
     * @param active {@code true} para permitir el acceso
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Cambia el perfil del usuario. Aplica en la siguiente petición con token, porque la autoridad
     * se construye con el rol guardado en la base y no con el claim del token.
     *
     * @param role nuevo perfil
     */
    public void changeRole(UserRole role) {
        this.role = role;
    }

    /**
     * Reemplaza el hash de la contraseña.
     *
     * @param passwordHash nuevo hash BCrypt
     */
    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * Nombre para mostrar en la interfaz: el nombre completo del médico cuando hay uno vinculado
     * o el nombre de usuario en caso contrario.
     *
     * @return el nombre visible del usuario
     */
    public String getDisplayName() {
        return doctor != null ? doctor.getFullName() : username;
    }

    /**
     * Identificador del médico vinculado.
     *
     * @return el id del médico o {@code null} si el usuario no es médico
     */
    public Long getDoctorId() {
        return doctor != null ? doctor.getId() : null;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getLastAccess() {
        return lastAccess;
    }
}
