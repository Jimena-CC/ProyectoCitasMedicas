package com.medicitas.kiosk.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ApiException")
class ApiExceptionTest {

    @Nested
    @DisplayName("Paciente no registrado (RF-01, RF-02)")
    class PatientNotFound {

        @Test
        @DisplayName("un 404 con código PATIENT_NOT_FOUND identifica a un paciente nuevo")
        void patientNotFoundCodeMeansNewPatient() {
            ApiException error = new ApiException(404, "PATIENT_NOT_FOUND", "No encontramos ese documento.", List.of());

            assertAll(
                    () -> assertTrue(error.isNotFound()),
                    () -> assertTrue(error.isPatientNotFound()));
        }

        @ParameterizedTest(name = "{0} {1} no es un paciente nuevo")
        @CsvSource({
                "404, RESOURCE_NOT_FOUND",
                "404, INTERNAL_ERROR",
                "200, PATIENT_NOT_FOUND",
                "500, INTERNAL_ERROR"
        })
        @DisplayName("otros 404 o códigos no se confunden con un paciente nuevo")
        void otherErrorsAreNotNewPatient(int status, String code) {
            ApiException error = new ApiException(status, code, null, List.of());

            assertFalse(error.isPatientNotFound());
        }

        @Test
        @DisplayName("un 404 sin cuerpo JSON (URL mal configurada) se trata como error genérico")
        void notFoundWithoutContractBodyIsGenericError() {
            ApiException error = new ApiException(404, null, null, List.of());

            assertAll(
                    () -> assertEquals("INTERNAL_ERROR", error.getCode()),
                    () -> assertFalse(error.isPatientNotFound()),
                    () -> assertFalse(error.getMessage().isBlank()));
        }
    }

    @Nested
    @DisplayName("Mensajes para el paciente (RNF-06)")
    class Messages {

        @Test
        @DisplayName("sin mensaje de la API se usa un texto genérico en español")
        void blankMessageFallsBackToGenericText() {
            ApiException error = new ApiException(500, "INTERNAL_ERROR", " ", null);

            assertAll(
                    () -> assertEquals("Ocurrió un problema al procesar tu solicitud. Inténtalo nuevamente.",
                            error.getMessage()),
                    () -> assertTrue(error.getFields().isEmpty()));
        }

        @Test
        @DisplayName("el mensaje de la API se conserva tal cual")
        void apiMessageIsKept() {
            ApiException error = new ApiException(409, "DUPLICATE_DOCUMENT", "Ese documento ya está registrado.", List.of());

            assertEquals("Ese documento ya está registrado.", error.getMessage());
        }
    }

    @Nested
    @DisplayName("Desenvolver errores asíncronos")
    class Unwrapping {

        @Test
        @DisplayName("encuentra la ApiException dentro de una CompletionException")
        void findsApiExceptionInsideCompletionException() {
            ApiException original = new ApiException(409, "SLOT_NOT_AVAILABLE", "Ese horario acaba de ser reservado.", List.of());

            ApiException found = ApiException.from(new CompletionException(new RuntimeException(original)));

            assertSame(original, found);
        }

        @Test
        @DisplayName("un error inesperado se convierte en INTERNAL_ERROR con mensaje apto para el paciente")
        void unexpectedErrorBecomesInternalError() {
            ApiException found = ApiException.from(new IllegalStateException("NullPointerException at line 42"));

            assertAll(
                    () -> assertEquals(500, found.getStatus()),
                    () -> assertEquals("INTERNAL_ERROR", found.getCode()),
                    () -> assertFalse(found.getMessage().contains("NullPointerException")));
        }
    }
}
