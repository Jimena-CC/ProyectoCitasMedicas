package com.medicitas.kiosk.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Cliente HTTP de la API MediCitas. Todas las llamadas son asíncronas y nunca
 * bloquean el hilo de JavaFX.
 */
public class ApiClient {

    public static final String DEFAULT_URL = "http://localhost:8080/api/v1";

    public static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);

    private final HttpClient http;
    private final String baseUrl;

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public static ApiClient fromProperties() {
        return new ApiClient(System.getProperty("api.url", DEFAULT_URL));
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public CompletableFuture<Dtos.Patient> findPatient(String documentType, String documentNumber) {
        return get("/patients?documentType=" + enc(documentType) + "&documentNumber=" + enc(documentNumber),
                new TypeReference<>() {
                });
    }

    public CompletableFuture<Dtos.Patient> registerPatient(Dtos.NewPatient newPatient) {
        return send("POST", "/patients", newPatient, null, new TypeReference<>() {
        });
    }

    public CompletableFuture<Dtos.Patient> updateInsurance(long patientId, Dtos.UpdateInsurance insurance) {
        return send("PUT", "/patients/" + patientId + "/insurance", insurance, null, new TypeReference<>() {
        });
    }

    public CompletableFuture<List<Dtos.Appointment>> upcomingAppointments(long patientId) {
        return get("/patients/" + patientId + "/appointments?upcoming=true", new TypeReference<>() {
        });
    }

    public CompletableFuture<List<Dtos.Insurer>> insurers() {
        return get("/insurers", new TypeReference<>() {
        });
    }

    public CompletableFuture<List<Dtos.Location>> locations() {
        return get("/locations", new TypeReference<>() {
        });
    }

    public CompletableFuture<List<Dtos.Specialty>> specialties(long locationId) {
        return get("/specialties?locationId=" + locationId, new TypeReference<>() {
        });
    }

    public CompletableFuture<Dtos.Availability> availability(long specialtyId, long locationId,
            LocalDate from, LocalDate to) {
        return get("/availability?specialtyId=" + specialtyId + "&locationId=" + locationId
                + "&from=" + from + "&to=" + to, new TypeReference<>() {
                });
    }

    public CompletableFuture<Dtos.Appointment> bookAppointment(Dtos.NewAppointment appointment, UUID idempotencyKey) {
        return send("POST", "/appointments", appointment, idempotencyKey, new TypeReference<>() {
        });
    }

    public CompletableFuture<Dtos.Appointment> rescheduleAppointment(long appointmentId, long newSlotId) {
        return send("PATCH", "/appointments/" + appointmentId + "/reschedule", new Dtos.Reschedule(newSlotId), null,
                new TypeReference<>() {
                });
    }

    public CompletableFuture<Dtos.Appointment> cancelAppointment(long appointmentId, String reason) {
        return send("PATCH", "/appointments/" + appointmentId + "/cancellation", new Dtos.Cancellation(reason), null,
                new TypeReference<>() {
                });
    }

    private <T> CompletableFuture<T> get(String path, TypeReference<T> type) {
        HttpRequest request = base(path).GET().build();
        return execute(request, type);
    }

    private <T> CompletableFuture<T> send(String method, String path, Object body, UUID idempotencyKey,
            TypeReference<T> type) {
        HttpRequest.Builder builder = base(path).header("Content-Type", "application/json");
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey.toString());
        }
        try {
            builder.method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        } catch (IOException e) {
            return CompletableFuture.failedFuture(new ApiException(400, "INVALID_DATA", null, List.of()));
        }
        return execute(builder.build(), type);
    }

    private HttpRequest.Builder base(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json");
    }

    private <T> CompletableFuture<T> execute(HttpRequest request, TypeReference<T> type) {
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .handle((response, error) -> {
                    if (error != null) {
                        throw ApiException.noConnection(error);
                    }
                    return convert(response, type);
                });
    }

    static <T> T convert(HttpResponse<String> response, TypeReference<T> type) {
        int status = response.statusCode();
        String body = response.body();
        try {
            if (status >= 200 && status < 300) {
                return JSON.readValue(body, type);
            }
            Dtos.ApiError err = body == null || body.isBlank() ? null : JSON.readValue(body, Dtos.ApiError.class);
            if (err == null) {
                throw new ApiException(status, null, null, List.of());
            }
            throw new ApiException(status, err.code(), err.message(), err.fields());
        } catch (IOException e) {
            throw new CompletionException(new ApiException(status, "INTERNAL_ERROR", null, List.of()));
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
