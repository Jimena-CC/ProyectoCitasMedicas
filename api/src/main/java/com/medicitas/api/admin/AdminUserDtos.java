package com.medicitas.api.admin;

import com.medicitas.api.security.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpos JSON de la gestión de usuarios del personal (issue #46). La respuesta de todos los
 * endpoints es {@link com.medicitas.api.security.UserSummary}; la contraseña nunca se devuelve.
 */
public final class AdminUserDtos {

    /** Longitud mínima de la contraseña. */
    public static final int MIN_PASSWORD_LENGTH = 8;

    /** Longitud máxima de la contraseña (límite de BCrypt). */
    public static final int MAX_PASSWORD_LENGTH = 72;

    private static final String PASSWORD_MESSAGE = "La contraseña debe tener entre 8 y 72 caracteres.";

    private AdminUserDtos() {
    }

    /**
     * Alta de un usuario.
     *
     * @param username nombre de usuario único (sin distinguir mayúsculas), de 3 a 50 caracteres
     * @param password contraseña en claro; se guarda solo su hash BCrypt
     * @param role     perfil del usuario
     * @param doctorId médico vinculado, obligatorio para {@code DOCTOR} y no permitido en los demás perfiles
     */
    public record CreateUserRequest(
            @NotBlank(message = "Ingresa el nombre de usuario.")
            @Size(min = 3, max = 50, message = "El nombre de usuario debe tener entre 3 y 50 caracteres.")
            @Pattern(regexp = "[A-Za-z0-9._-]+",
                    message = "El nombre de usuario solo admite letras, números, punto, guion y guion bajo.")
            String username,

            @NotBlank(message = "Ingresa la contraseña.")
            @Size(min = MIN_PASSWORD_LENGTH, max = MAX_PASSWORD_LENGTH, message = PASSWORD_MESSAGE)
            String password,

            @NotNull(message = "Selecciona el perfil del usuario.")
            UserRole role,

            Long doctorId) {
    }

    /**
     * Edición de un usuario: perfil, médico vinculado y estado. El nombre de usuario no se cambia.
     *
     * @param role     perfil del usuario
     * @param doctorId médico vinculado, obligatorio para {@code DOCTOR} y no permitido en los demás perfiles
     * @param active   {@code true} para permitir el acceso; un administrador no puede desactivarse a sí mismo
     */
    public record UpdateUserRequest(
            @NotNull(message = "Selecciona el perfil del usuario.")
            UserRole role,

            Long doctorId,

            @NotNull(message = "Indica si el usuario está activo.")
            Boolean active) {
    }

    /**
     * Restablecimiento de contraseña por el administrador.
     *
     * @param password nueva contraseña en claro
     */
    public record PasswordChangeRequest(
            @NotBlank(message = "Ingresa la nueva contraseña.")
            @Size(min = MIN_PASSWORD_LENGTH, max = MAX_PASSWORD_LENGTH, message = PASSWORD_MESSAGE)
            String password) {
    }
}
