package com.medicitas.api.notification;

import java.util.List;

/**
 * Notificaciones inmediatas que deben enviarse cuando la transacción que las creó confirme.
 */
public record NotificationsToSendEvent(List<Long> notificationIds) {
}
