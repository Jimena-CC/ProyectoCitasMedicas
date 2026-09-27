package com.medicitas.api.notification;

public record OutgoingMessage(NotificationChannel channel, String destination, String subject, String body) {
}
