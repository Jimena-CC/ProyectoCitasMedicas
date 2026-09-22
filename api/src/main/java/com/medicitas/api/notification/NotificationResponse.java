package com.medicitas.api.notification;

import com.medicitas.api.common.Formats;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.OffsetDateTime;

public record NotificationResponse(
        NotificationType type,
        NotificationChannel channel,
        NotificationStatus status,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Formats.DATE_TIME) OffsetDateTime scheduledFor,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Formats.DATE_TIME) OffsetDateTime sentAt,
        int attempts) {
}
