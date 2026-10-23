package com.medicitas.kiosk.api;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ApiClient")
class ApiClientTest {

    private static final TypeReference<Dtos.Patient> PATIENT = new TypeReference<>() { };

    @Nested
    @DisplayName("Conversión de respuestas HTTP")
    class Convert {

        @Test
        @DisplayName("un 2xx se deserializa al DTO del contrato")
        void successIsDeserialized() {
            String body = """
                    { "id": 1, "documentType": "DNI", "documentNumber": "45871236",
                      "firstNames": "Lucía", "lastNames": "Paredes Quispe", "insurance": null }""";

            Dtos.Patient patient = ApiClient.convert(response(200, body), PATIENT);

            assertEquals("45871236", patient.documentNumber());
        }

        @Test
        @DisplayName("un 404 PATIENT_NOT_FOUND del contrato se reconoce como paciente nuevo")
        void contractNotFoundIsPatientNotFound() {
            String body = """
                    { "code": "PATIENT_NOT_FOUND", "message": "No encontramos ese documento.",
                      "fields": [], "timestamp": "2026-09-15T21:42:10-05:00" }""";

            ApiException error = assertThrows(ApiException.class, () -> ApiClient.convert(response(404, body), PATIENT));

            assertAll(
                    () -> assertTrue(error.isPatientNotFound()),
                    () -> assertEquals("No encontramos ese documento.", error.getMessage()));
        }

        @Test
        @DisplayName("un 404 ajeno al contrato (URL mal configurada) no se confunde con paciente nuevo")
        void foreignNotFoundIsNotPatientNotFound() {
            String springDefault = """
                    { "timestamp": "2026-09-15T21:42:10-05:00", "status": 404, "error": "Not Found", "path": "/x" }""";

            ApiException error = assertThrows(ApiException.class,
                    () -> ApiClient.convert(response(404, springDefault), PATIENT));

            assertAll(
                    () -> assertEquals(404, error.getStatus()),
                    () -> assertFalse(error.isPatientNotFound()),
                    () -> assertFalse(error.getMessage().contains("Not Found"), "sin tecnicismos (RNF-06)"));
        }

        @Test
        @DisplayName("un cuerpo que no es JSON (por ejemplo HTML de un proxy) produce un error genérico")
        void nonJsonBodyBecomesGenericError() {
            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> ApiClient.convert(response(502, "<html>Bad Gateway</html>"), PATIENT));

            ApiException error = ApiException.from(thrown);
            assertAll(
                    () -> assertEquals(502, error.getStatus()),
                    () -> assertEquals("INTERNAL_ERROR", error.getCode()),
                    () -> assertFalse(error.getMessage().contains("html")));
        }
    }

    @Nested
    @DisplayName("URL base")
    class BaseUrl {

        @Test
        @DisplayName("se elimina la barra final para componer rutas sin dobles barras")
        void trailingSlashIsRemoved() {
            assertEquals("http://localhost:8080/api/v1", new ApiClient("http://localhost:8080/api/v1/").getBaseUrl());
        }
    }

    /** Respuesta HTTP mínima para probar la conversión sin levantar un servidor. */
    private static HttpResponse<String> response(int status, String body) {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return status;
            }

            @Override
            public HttpRequest request() {
                return HttpRequest.newBuilder(uri()).build();
            }

            @Override
            public Optional<HttpResponse<String>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(Map.of(), (a, b) -> true);
            }

            @Override
            public String body() {
                return body;
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return URI.create("http://localhost:8080/api/v1/patients");
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }
}
