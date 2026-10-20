package com.medicitas.api.audit;

/**
 * Acciones registradas en {@code audit_log}. Cada acción declara la entidad sobre la que actúa.
 */
public enum AuditAction {

    APPOINTMENT_BOOKED(AuditEntities.APPOINTMENT),
    APPOINTMENT_RESCHEDULED(AuditEntities.APPOINTMENT),
    APPOINTMENT_CANCELLED(AuditEntities.APPOINTMENT),
    USER_LOGGED_IN(AuditEntities.APP_USER);

    private final String entity;

    AuditAction(String entity) {
        this.entity = entity;
    }

    /**
     * Nombre de la entidad afectada, tal como se guarda en la columna {@code entity}.
     *
     * @return el nombre de la entidad
     */
    public String entity() {
        return entity;
    }

    /**
     * Nombres de entidad usados en la columna {@code entity} de {@code audit_log}.
     */
    public static final class AuditEntities {

        public static final String APPOINTMENT = "APPOINTMENT";
        public static final String APP_USER = "APP_USER";

        private AuditEntities() {
        }
    }
}
