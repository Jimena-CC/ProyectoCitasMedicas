package com.medicitas.api.common;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Forma única de las respuestas de error. {@code message} se muestra tal cual al paciente (RNF-06).
 */
public record ErrorResponse(
        String code,
        String message,
        List<FieldError> fields,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Formats.DATE_TIME) OffsetDateTime timestamp) {

    public record FieldError(String field, String message) {
    }
}
