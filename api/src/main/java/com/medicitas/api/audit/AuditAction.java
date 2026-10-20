package com.medicitas.api.audit;

/**
 * Acciones registradas en {@code audit_log}. Cada acción declara la entidad sobre la que actúa.
 */
public enum AuditAction {

    /** Reserva de una cita (RF-06), desde el kiosko o por el personal. */
    APPOINTMENT_BOOKED(AuditEntities.APPOINTMENT),

    /** Reprogramación de una cita a otro cupo (RF-09). */
    APPOINTMENT_RESCHEDULED(AuditEntities.APPOINTMENT),

    /** Anulación de una cita (RF-10). */
    APPOINTMENT_CANCELLED(AuditEntities.APPOINTMENT),

    /** Inicio de sesión correcto de un usuario del personal. */
    USER_LOGGED_IN(AuditEntities.APP_USER),

    // Issue #45: módulos de médico y admisión
    /** Registro de asistencia o inasistencia de una cita por el médico (CU-012). */
    ATTENDANCE_REGISTERED(AuditEntities.APPOINTMENT);

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

        /** Cita médica (tabla {@code appointment}). */
        public static final String APPOINTMENT = "APPOINTMENT";

        /** Usuario del personal (tabla {@code app_user}). */
        public static final String APP_USER = "APP_USER";

        private AuditEntities() {
        }
    }
}
