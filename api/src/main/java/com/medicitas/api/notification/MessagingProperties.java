package com.medicitas.api.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración del envío de notificaciones (prefijo {@code messaging}).
 *
 * @param simulateFailure    fuerza que el proveedor simulado rechace los envíos (RF-14)
 * @param maxAttempts        intentos antes de marcar la notificación como FAILED
 * @param retryMinutes       espera entre intentos fallidos
 * @param reminderHoursAhead anticipación del recordatorio respecto del inicio de la cita (RF-12)
 */
@ConfigurationProperties(prefix = "messaging")
public record MessagingProperties(
        @DefaultValue("false") boolean simulateFailure,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("10") int retryMinutes,
        @DefaultValue("24") int reminderHoursAhead) {
}
