package com.medicitas.api.audit;

import com.medicitas.api.security.AppUser;
import com.medicitas.api.security.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Instant;

/**
 * Escribe en {@code audit_log}. Resuelve por sí mismo el usuario autenticado (o ninguno si la llamada
 * viene del kiosko) y la IP de la petición HTTP en curso, si la hay.
 */
@Service
public class AuditService {

    /** Cabecera que agregan los proxies con la IP original del cliente. */
    static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private static final int MAX_IP_LENGTH = 45;

    private final AuditLogRepository auditLogRepository;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public AuditService(AuditLogRepository auditLogRepository, CurrentUserService currentUserService, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    /**
     * Registra una acción atribuida al usuario autenticado en la petición actual, o a nadie si es el kiosko.
     * Participa en la transacción de negocio en curso: si esta se revierte, el registro también.
     *
     * @param action   acción realizada
     * @param entityId id de la entidad afectada, si aplica
     * @param detail   descripción legible de lo ocurrido
     * @return el registro guardado
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditLog record(AuditAction action, Long entityId, String detail) {
        return record(currentUserService.current().orElse(null), action, entityId, detail);
    }

    /**
     * Registra una acción atribuida a un usuario conocido; útil cuando aún no hay sesión, como en el login.
     *
     * @param user     usuario responsable o {@code null} para el kiosko
     * @param action   acción realizada
     * @param entityId id de la entidad afectada, si aplica
     * @param detail   descripción legible de lo ocurrido
     * @return el registro guardado
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditLog record(AppUser user, AuditAction action, Long entityId, String detail) {
        return record(user, action.name(), action.entity(), entityId, detail);
    }

    /**
     * Registra una acción arbitraria; los demás métodos delegan aquí.
     *
     * @param user     usuario responsable o {@code null} para el kiosko
     * @param action   nombre de la acción (máximo 60 caracteres)
     * @param entity   entidad afectada (máximo 40 caracteres)
     * @param entityId id de la entidad afectada, si aplica
     * @param detail   descripción legible de lo ocurrido
     * @return el registro guardado
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditLog record(AppUser user, String action, String entity, Long entityId, String detail) {
        AuditLog entry = new AuditLog(user, action, entity, entityId, detail, currentSourceIp(), Instant.now(clock));
        return auditLogRepository.save(entry);
    }

    /**
     * IP de origen de la petición HTTP en curso: la primera de {@code X-Forwarded-For} si existe,
     * o la dirección remota de la conexión.
     *
     * @return la IP, o {@code null} fuera de una petición HTTP (tareas programadas, pruebas unitarias)
     */
    static String currentSourceIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        HttpServletRequest request = servletAttributes.getRequest();
        String forwarded = request.getHeader(FORWARDED_FOR_HEADER);
        String ip = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        if (ip == null || ip.isBlank()) {
            return null;
        }
        return ip.length() > MAX_IP_LENGTH ? ip.substring(0, MAX_IP_LENGTH) : ip;
    }
}
