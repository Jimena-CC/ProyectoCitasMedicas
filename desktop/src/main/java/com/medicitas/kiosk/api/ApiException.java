package com.medicitas.kiosk.api;

import java.util.List;

/**
 * Error devuelto por la API o por la conexión. {@link #getMessage()} siempre es apto para mostrarse al paciente.
 */
public class ApiException extends RuntimeException {

    public static final String NO_CONNECTION = "NO_CONNECTION";
    public static final String NO_CONNECTION_MESSAGE =
            "No pudimos conectar con el sistema de citas. Inténtalo nuevamente en unos segundos.";
    private static final String GENERIC_MESSAGE =
            "Ocurrió un problema al procesar tu solicitud. Inténtalo nuevamente.";

    private final int status;
    private final String code;
    private final transient List<Dtos.FieldError> fields;

    public ApiException(int status, String code, String message, List<Dtos.FieldError> fields) {
        super(message == null || message.isBlank() ? GENERIC_MESSAGE : message);
        this.status = status;
        this.code = code == null ? "INTERNAL_ERROR" : code;
        this.fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public static ApiException noConnection(Throwable cause) {
        ApiException e = new ApiException(0, NO_CONNECTION, NO_CONNECTION_MESSAGE, List.of());
        e.initCause(cause);
        return e;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<Dtos.FieldError> getFields() {
        return fields;
    }

    public boolean isNoConnection() {
        return NO_CONNECTION.equals(code);
    }

    public boolean isNotFound() {
        return status == 404;
    }

    /** Conflictos de agenda que obligan a elegir otro horario (RF-07, RF-08). */
    public boolean isScheduleConflict() {
        return "SLOT_NOT_AVAILABLE".equals(code) || "DUPLICATE_APPOINTMENT".equals(code);
    }

    /** Desenvuelve CompletionException/ExecutionException hasta encontrar una ApiException. */
    public static ApiException from(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof ApiException api) {
                return api;
            }
            current = current.getCause();
        }
        return new ApiException(500, "INTERNAL_ERROR", null, List.of());
    }
}
