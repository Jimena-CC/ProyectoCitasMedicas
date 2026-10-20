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

    // Issue #46: administración (usuarios, catálogos y agendas)

    /** Alta de un usuario del personal por el administrador. */
    USER_CREATED(AuditEntities.APP_USER),

    /** Cambio de perfil, médico vinculado o reactivación de un usuario. */
    USER_UPDATED(AuditEntities.APP_USER),

    /** Desactivación de un usuario: deja de poder iniciar sesión. */
    USER_DEACTIVATED(AuditEntities.APP_USER),

    /** Restablecimiento de la contraseña de un usuario por el administrador. */
    USER_PASSWORD_CHANGED(AuditEntities.APP_USER),

    /** Alta de una especialidad y de las sedes donde se atiende. */
    SPECIALTY_CREATED(AuditEntities.SPECIALTY),

    /** Cambio de datos, sedes o estado de una especialidad. */
    SPECIALTY_UPDATED(AuditEntities.SPECIALTY),

    /** Alta de un médico. */
    DOCTOR_CREATED(AuditEntities.DOCTOR),

    /** Cambio de datos, especialidad o estado de un médico. */
    DOCTOR_UPDATED(AuditEntities.DOCTOR),

    /** Generación de cupos de agenda para un médico en una sede. */
    SLOTS_GENERATED(AuditEntities.SLOT),

    /** Bloqueo de un cupo libre para que no se pueda reservar. */
    SLOT_BLOCKED(AuditEntities.SLOT),

    /** Desbloqueo de un cupo: vuelve a estar disponible. */
    SLOT_UNBLOCKED(AuditEntities.SLOT);

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

        // Issue #46: entidades del catálogo y de la agenda

        /** Especialidad (tabla {@code specialty}). */
        public static final String SPECIALTY = "SPECIALTY";

        /** Médico (tabla {@code doctor}). */
        public static final String DOCTOR = "DOCTOR";

        /** Cupo de agenda (tabla {@code slot}). */
        public static final String SLOT = "SLOT";

        private AuditEntities() {
        }
    }
}
