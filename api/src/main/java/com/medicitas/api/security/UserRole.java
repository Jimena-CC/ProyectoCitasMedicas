package com.medicitas.api.security;

/**
 * Perfiles del personal de la clínica que acceden a la API con usuario y contraseña.
 * El paciente del kiosko no tiene usuario: sus endpoints son públicos.
 */
public enum UserRole {

    /** Administrador del sistema: gestiona usuarios, catálogos y consulta la auditoría. */
    ADMIN,

    /** Personal de admisión: atiende la central de citas y la recepción. */
    FRONT_DESK,

    /** Médico: consulta su agenda y registra la atención de sus citas. */
    DOCTOR;

    private static final String AUTHORITY_PREFIX = "ROLE_";

    /**
     * Nombre de la autoridad de Spring Security asociada al perfil, por ejemplo {@code ROLE_ADMIN}.
     *
     * @return la autoridad con el prefijo {@code ROLE_}
     */
    public String authority() {
        return AUTHORITY_PREFIX + name();
    }
}
