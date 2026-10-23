package com.medicitas.api.security;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.medicitas.api.common.Formats;
import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;

/**
 * Cuerpos JSON del inicio de sesión y del usuario autenticado.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /**
     * Credenciales enviadas por el panel del personal.
     *
     * @param username nombre de usuario
     * @param password contraseña en claro; solo viaja por HTTPS y nunca se guarda
     */
    public record LoginRequest(
            @NotBlank(message = "Ingresa tu usuario.")
            String username,

            @NotBlank(message = "Ingresa tu contraseña.")
            String password) {
    }

    /**
     * Resultado del inicio de sesión.
     *
     * @param token     JWT que el cliente envía en la cabecera {@code Authorization: Bearer <token>}
     * @param expiresAt instante en que el token deja de ser válido
     * @param user      datos del usuario autenticado
     */
    public record LoginResponse(
            String token,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Formats.DATE_TIME) OffsetDateTime expiresAt,
            UserInfo user) {
    }

    /**
     * Datos públicos del usuario autenticado.
     *
     * @param id       identificador en {@code app_user}
     * @param username nombre de usuario
     * @param role     perfil
     * @param fullName nombre para mostrar: el del médico vinculado o el nombre de usuario
     * @param doctorId id del médico vinculado o {@code null}
     */
    public record UserInfo(Long id, String username, UserRole role, String fullName, Long doctorId) {

        /**
         * Construye la vista pública a partir de la entidad.
         *
         * @param user usuario autenticado
         * @return la vista sin datos sensibles
         */
        public static UserInfo from(AppUser user) {
            return new UserInfo(user.getId(), user.getUsername(), user.getRole(), user.getDisplayName(),
                    user.getDoctorId());
        }
    }
}
