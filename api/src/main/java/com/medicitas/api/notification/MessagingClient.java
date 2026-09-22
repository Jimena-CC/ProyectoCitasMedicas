package com.medicitas.api.notification;

/**
 * Puerto hacia el Servicio de Mensajería Externalizado (correo / SMS).
 */
public interface MessagingClient {

    /**
     * Envía el mensaje y devuelve el identificador asignado por el proveedor.
     */
    String send(OutgoingMessage message) throws MessagingException;
}
