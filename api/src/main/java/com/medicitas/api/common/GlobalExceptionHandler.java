package com.medicitas.api.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Traduce toda excepción a {@link ErrorResponse}. Nunca expone stacktraces ni SQL (RNF-06).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String INVALID_DATA_MESSAGE = "Revisa los datos ingresados e inténtalo nuevamente.";

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getFields());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return invalidData(fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleInvalidParameters(HandlerMethodValidationException ex) {
        List<ErrorResponse.FieldError> fields = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ErrorResponse.FieldError(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return invalidData(fields);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        return invalidData(List.of(new ErrorResponse.FieldError(ex.getParameterName(), "Este dato es obligatorio.")));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        return invalidData(List.of(new ErrorResponse.FieldError(ex.getHeaderName(), "Falta esta cabecera obligatoria.")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleInvalidType(MethodArgumentTypeMismatchException ex) {
        return invalidData(List.of(new ErrorResponse.FieldError(ex.getName(), "El valor no tiene el formato esperado.")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        logger.debug("Cuerpo de solicitud ilegible: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_DATA,
                "La solicitud no tiene un formato válido. Revisa los datos e inténtalo nuevamente.", List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleMissingResource(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ErrorCodes.RESOURCE_NOT_FOUND, "El recurso solicitado no existe.", List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ErrorCodes.INVALID_DATA,
                "Esta operación no está disponible.", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        logger.error("Error no controlado al procesar la solicitud", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR,
                "Ocurrió un problema al procesar tu solicitud. Inténtalo nuevamente en unos minutos.", List.of());
    }

    private ResponseEntity<ErrorResponse> invalidData(List<ErrorResponse.FieldError> fields) {
        String message = fields.size() == 1 ? fields.getFirst().message() : INVALID_DATA_MESSAGE;
        return build(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_DATA, message, fields);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatusCode status, String code, String message,
                                                List<ErrorResponse.FieldError> fields) {
        ErrorResponse body = new ErrorResponse(code, message, fields, OffsetDateTime.now(clock));
        return ResponseEntity.status(status).body(body);
    }
}
