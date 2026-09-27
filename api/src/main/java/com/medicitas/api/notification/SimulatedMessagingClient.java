package com.medicitas.api.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Proveedor simulado: registra el envío en el log en lugar de contactar un servicio real.
 * Con {@code messaging.simulate-failure=true} rechaza todos los envíos para demostrar RF-14.
 */
@Component
public class SimulatedMessagingClient implements MessagingClient {

    private static final Logger logger = LoggerFactory.getLogger(SimulatedMessagingClient.class);

    private final AtomicBoolean simulateFailure;

    public SimulatedMessagingClient(MessagingProperties properties) {
        this.simulateFailure = new AtomicBoolean(properties.simulateFailure());
    }

    @Override
    public String send(OutgoingMessage message) throws MessagingException {
        if (simulateFailure.get()) {
            logger.warn("Proveedor simulado rechazó el {} para {}", message.channel(), mask(message.destination()));
            throw new MessagingException("El proveedor de mensajería no respondió (falla simulada).");
        }
        String messageId = "SIM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        logger.info("Mensaje {} enviado por {} a {}: {}", messageId, message.channel(), mask(message.destination()),
                message.subject());
        return messageId;
    }

    public void setSimulateFailure(boolean simulateFailure) {
        this.simulateFailure.set(simulateFailure);
    }

    /** Evita escribir correos y celulares completos en el log (Ley 29733). */
    private static String mask(String destination) {
        if (destination == null || destination.length() <= 4) {
            return "****";
        }
        return destination.substring(0, 2) + "****" + destination.substring(destination.length() - 2);
    }
}
