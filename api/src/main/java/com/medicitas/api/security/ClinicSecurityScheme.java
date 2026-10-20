package com.medicitas.api.security;

/**
 * Nombre del esquema de seguridad declarado en OpenAPI; los endpoints protegidos lo referencian
 * con {@code @SecurityRequirement} para que Swagger UI permita autorizar con el token.
 */
public final class ClinicSecurityScheme {

    public static final String BEARER = "bearerAuth";

    private ClinicSecurityScheme() {
    }
}
