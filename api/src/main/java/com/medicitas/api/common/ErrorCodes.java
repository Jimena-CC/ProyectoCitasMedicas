package com.medicitas.api.common;

/**
 * Códigos de error del contrato de la API (docs/api-contract.md, sección 5).
 */
public final class ErrorCodes {

    public static final String INVALID_DATA = "INVALID_DATA";
    public static final String PATIENT_NOT_FOUND = "PATIENT_NOT_FOUND";
    public static final String APPOINTMENT_NOT_FOUND = "APPOINTMENT_NOT_FOUND";
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String DUPLICATE_DOCUMENT = "DUPLICATE_DOCUMENT";
    public static final String SLOT_NOT_AVAILABLE = "SLOT_NOT_AVAILABLE";
    public static final String DUPLICATE_APPOINTMENT = "DUPLICATE_APPOINTMENT";
    public static final String COVERAGE_NOT_ACTIVE = "COVERAGE_NOT_ACTIVE";
    public static final String APPOINTMENT_NOT_MODIFIABLE = "APPOINTMENT_NOT_MODIFIABLE";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ErrorCodes() {
    }
}
