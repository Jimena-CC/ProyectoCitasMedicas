package com.medicitas.api.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Proceso 07: cada 5 minutos entrega los recordatorios vencidos y reintenta los envíos fallidos.
 */
@Component
public class ScheduledNotificationsJob {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledNotificationsJob.class);

    private final NotificationService notificationService;

    public ScheduledNotificationsJob(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelayString = "${messaging.job-interval-ms:300000}",
            initialDelayString = "${messaging.job-interval-ms:300000}")
    public void processPending() {
        List<Long> pending = notificationService.duePendingIds();
        for (Long notificationId : pending) {
            try {
                notificationService.send(notificationId);
            } catch (RuntimeException ex) {
                logger.error("Error inesperado al procesar la notificación {}", notificationId, ex);
            }
        }
        if (!pending.isEmpty()) {
            logger.info("Tarea de notificaciones: {} notificación(es) procesada(s)", pending.size());
        }
    }
}
