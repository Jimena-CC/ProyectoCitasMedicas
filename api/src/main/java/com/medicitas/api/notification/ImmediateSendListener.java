package com.medicitas.api.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envía las notificaciones inmediatas solo después de confirmar la cita y fuera del hilo de la
 * solicitud, para que el proveedor externo no retrase ni revierta la reserva (RF-11, RF-14).
 */
@Component
public class ImmediateSendListener {

    private static final Logger logger = LoggerFactory.getLogger(ImmediateSendListener.class);

    private final NotificationService notificationService;

    public ImmediateSendListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendAfterCommit(NotificationsToSendEvent event) {
        for (Long notificationId : event.notificationIds()) {
            try {
                notificationService.send(notificationId);
            } catch (RuntimeException ex) {
                logger.error("Error inesperado al enviar la notificación {}", notificationId, ex);
            }
        }
    }
}
