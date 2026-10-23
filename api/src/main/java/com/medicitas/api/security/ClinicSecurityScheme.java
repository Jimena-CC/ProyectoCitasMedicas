package com.medicitas.api.security;

/**
 * Nombre del esquema de seguridad declarado en OpenAPI; los endpoints protegidos lo referencian
 * con {@code @SecurityRequirement} para que Swagger UI permita autorizar con el token.
 */
public final class ClinicSecurityScheme {

    /** Nombre del esquema {@code bearer} (JWT) registrado en el documento OpenAPI. */
    public static final String BEARER = "bearerAuth";

    private ClinicSecurityScheme() {
    }
}
