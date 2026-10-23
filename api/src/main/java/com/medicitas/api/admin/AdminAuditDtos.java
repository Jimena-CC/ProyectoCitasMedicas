package com.medicitas.api.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.medicitas.api.audit.AuditLog;
import com.medicitas.api.common.Formats;
import com.medicitas.api.security.AppUser;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Respuestas de la consulta de auditoría del administrador (issue #46, RF-20).
 */
public final class AdminAuditDtos {

    private AdminAuditDtos() {
    }

    /**
     * Usuario responsable de un registro.
     *
     * @param id       identificador en {@code app_user}
     * @param username nombre de usuario
     * @param fullName nombre para mostrar
     */
    public record AuditUser(Long id, String username, String fullName) {

        /**
         * Construye la vista del usuario.
         *
         * @param user usuario con su médico cargado
         * @return la vista
         */
        public static AuditUser from(AppUser user) {
            return new AuditUser(user.getId(), user.getUsername(), user.getDisplayName());
        }
    }

    /**
     * Fila del registro de auditoría.
     *
     * @param id           identificador
     * @param registeredAt instante del registro en la zona de la clínica
     * @param user         usuario responsable o {@code null} si actuó el paciente desde el kiosko
     * @param action       acción, por ejemplo {@code APPOINTMENT_BOOKED}
     * @param entity       entidad afectada, por ejemplo {@code APPOINTMENT}
     * @param entityId     id de la entidad afectada o {@code null}
     * @param detail       descripción legible de lo ocurrido
     * @param sourceIp     IP de origen o {@code null}
     */
    public record AuditEntry(
            Long id,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Formats.DATE_TIME) OffsetDateTime registeredAt,
            AuditUser user,
            String action,
            String entity,
            Long entityId,
            String detail,
            String sourceIp) {

        /**
         * Construye la fila a partir de la entidad.
         *
         * @param log  registro con su usuario cargado
         * @param zone zona horaria de la clínica
         * @return la fila
         */
        public static AuditEntry from(AuditLog log, ZoneId zone) {
            return new AuditEntry(log.getId(), log.getRegisteredAt().atZone(zone).toOffsetDateTime(),
                    log.getUser() == null ? null : AuditUser.from(log.getUser()), log.getAction(), log.getEntity(),
                    log.getEntityId(), log.getDetail(), log.getSourceIp());
        }
    }

    /**
     * Página de resultados.
     *
     * @param content       filas de la página
     * @param page          número de página, desde 0
     * @param size          tamaño de página solicitado
     * @param totalElements total de filas que cumplen los filtros
     * @param totalPages    total de páginas
     * @param <T>           tipo de fila
     */
    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }
}
