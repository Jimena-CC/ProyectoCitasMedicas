package com.medicitas.api.admin;

import com.medicitas.api.admin.AdminAuditDtos.AuditEntry;
import com.medicitas.api.admin.AdminAuditDtos.PageResponse;
import com.medicitas.api.audit.AuditLog;
import com.medicitas.api.audit.AuditLogRepository;
import com.medicitas.api.common.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Consulta paginada del registro de auditoría (CU-024, RF-20) sobre {@link AuditLogRepository#search}.
 * Las fechas se interpretan en la zona horaria de la clínica: {@code from} desde las 00:00 de ese día y
 * {@code to} hasta el final de ese día (ambos inclusive).
 */
@Service
public class AdminAuditService {

    /** Tamaño máximo de página. */
    public static final int MAX_PAGE_SIZE = 100;

    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public AdminAuditService(AuditLogRepository auditLogRepository, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    /**
     * Busca registros del más reciente al más antiguo; todo filtro nulo se ignora.
     *
     * @param entity entidad afectada, por ejemplo {@code APPOINTMENT}
     * @param action acción, por ejemplo {@code APPOINTMENT_BOOKED}
     * @param userId id del usuario responsable
     * @param from   primera fecha (inclusive)
     * @param to     última fecha (inclusive)
     * @param page   número de página, desde 0
     * @param size   tamaño de página, de 1 a {@link #MAX_PAGE_SIZE}
     * @return la página de registros
     * @throws BusinessException {@code 400 INVALID_DATA} si la página, el tamaño o el rango no son válidos
     */
    @Transactional(readOnly = true)
    public PageResponse<AuditEntry> search(String entity, String action, Long userId, LocalDate from, LocalDate to,
                                           int page, int size) {
        if (page < 0) {
            throw BusinessException.invalidData("page", "La página debe ser 0 o mayor.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw BusinessException.invalidData("size", "El tamaño de página debe estar entre 1 y " + MAX_PAGE_SIZE + ".");
        }
        if (from != null && to != null && to.isBefore(from)) {
            throw BusinessException.invalidData("to", "La fecha final debe ser igual o posterior a la fecha inicial.");
        }
        Instant fromInstant = from == null ? null : from.atStartOfDay(clock.getZone()).toInstant();
        Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        Page<AuditLog> result = auditLogRepository.search(blankToNull(entity), blankToNull(action), userId,
                fromInstant, toInstant, PageRequest.of(page, size));
        return new PageResponse<>(
                result.getContent().stream().map(log -> AuditEntry.from(log, clock.getZone())).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
