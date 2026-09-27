package com.medicitas.api.notification;

/**
 * El proveedor externo de mensajería rechazó el envío o no respondió.
 */
public class MessagingException extends Exception {

    public MessagingException(String message) {
        super(message);
    }

    public MessagingException(String message, Throwable cause) {
        super(message, cause);
    }
}
