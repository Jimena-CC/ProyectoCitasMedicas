package com.medicitas.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Configuración de seguridad (prefijo {@code security}).
 *
 * @param jwt  firma y vigencia de los tokens
 * @param cors orígenes del navegador autorizados a llamar a la API
 */
@ConfigurationProperties(prefix = "security")
public record SecurityProperties(@DefaultValue Jwt jwt, @DefaultValue Cors cors) {

    /**
     * Parámetros del token JWT firmado con clave simétrica HMAC.
     *
     * @param secret            clave de firma; debe tener al menos 32 caracteres para HS256
     * @param expirationMinutes minutos de vigencia del token desde su emisión
     */
    public record Jwt(String secret, @DefaultValue("480") long expirationMinutes) {
    }

    /**
     * Configuración CORS para los paneles web del personal.
     *
     * @param allowedOrigins orígenes permitidos, por ejemplo {@code http://localhost:5173}
     */
    public record Cors(@DefaultValue("http://localhost:5173") List<String> allowedOrigins) {
    }
}
