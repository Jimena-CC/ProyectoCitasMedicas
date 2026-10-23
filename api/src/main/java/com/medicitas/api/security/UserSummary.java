package com.medicitas.api.security;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.medicitas.api.common.Formats;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Fila del listado de usuarios del panel de administración.
 *
 * @param id         identificador en {@code app_user}
 * @param username   nombre de usuario
 * @param role       perfil
 * @param fullName   nombre para mostrar
 * @param doctorId   id del médico vinculado o {@code null}
 * @param active     si puede iniciar sesión
 * @param lastAccess último inicio de sesión o {@code null} si nunca ingresó
 */
public record UserSummary(
        Long id,
        String username,
        UserRole role,
        String fullName,
        Long doctorId,
        boolean active,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Formats.DATE_TIME) OffsetDateTime lastAccess) {

    /**
     * Construye la fila a partir de la entidad.
     *
     * @param user usuario
     * @param zone zona horaria de la clínica para presentar el último acceso
     * @return la fila del listado
     */
    public static UserSummary from(AppUser user, ZoneId zone) {
        OffsetDateTime lastAccess = user.getLastAccess() == null
                ? null
                : user.getLastAccess().atZone(zone).toOffsetDateTime();
        return new UserSummary(user.getId(), user.getUsername(), user.getRole(), user.getDisplayName(),
                user.getDoctorId(), user.isActive(), lastAccess);
    }
}
