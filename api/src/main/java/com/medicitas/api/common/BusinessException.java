package com.medicitas.api.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.util.List;

/**
 * Error de negocio con un código del contrato y un mensaje apto para el paciente.
 */
public class BusinessException extends RuntimeException {

    private static final HttpStatusCode UNPROCESSABLE = HttpStatusCode.valueOf(422);

    private final HttpStatusCode status;
    private final String code;
    private final List<ErrorResponse.FieldError> fields;

    public BusinessException(HttpStatusCode status, String code, String message, List<ErrorResponse.FieldError> fields) {
        super(message);
        this.status = status;
        this.code = code;
        this.fields = List.copyOf(fields);
    }

    public static BusinessException notFound(String code, String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, code, message, List.of());
    }

    public static BusinessException conflict(String code, String message) {
        return new BusinessException(HttpStatus.CONFLICT, code, message, List.of());
    }

    public static BusinessException ruleViolated(String code, String message) {
        return new BusinessException(UNPROCESSABLE, code, message, List.of());
    }

    /**
     * Error {@code 401}: no hay sesión o las credenciales no son válidas.
     *
     * @param code    código del contrato, por ejemplo {@link ErrorCodes#UNAUTHORIZED}
     * @param message mensaje para el usuario
     * @return la excepción
     */
    public static BusinessException unauthorized(String code, String message) {
        return new BusinessException(HttpStatus.UNAUTHORIZED, code, message, List.of());
    }

    /**
     * Error {@code 403}: hay sesión pero el usuario no puede realizar la acción.
     *
     * @param code    código del contrato, por ejemplo {@link ErrorCodes#FORBIDDEN}
     * @param message mensaje para el usuario
     * @return la excepción
     */
    public static BusinessException forbidden(String code, String message) {
        return new BusinessException(HttpStatus.FORBIDDEN, code, message, List.of());
    }

    public static BusinessException invalidData(String field, String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_DATA, message,
                List.of(new ErrorResponse.FieldError(field, message)));
    }

    public HttpStatusCode getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<ErrorResponse.FieldError> getFields() {
        return fields;
    }
}
