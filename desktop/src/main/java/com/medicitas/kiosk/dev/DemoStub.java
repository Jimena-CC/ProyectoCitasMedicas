package com.medicitas.kiosk.dev;

import com.medicitas.kiosk.api.ApiClient;
import com.medicitas.kiosk.api.Dtos;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * API simulada en memoria para mostrar el kiosko sin levantar la API ni PostgreSQL.
 *
 * <p>Solo se usa con el argumento {@code --demo-stub}. No forma parte del flujo normal:
 * la aplicación la carga por reflexión y nunca la referencia en tiempo de compilación.
 * Los datos son ficticios y siguen los ejemplos de {@code docs/api-contract.md}.</p>
 */
public final class DemoStub implements AutoCloseable {

    private static final String BASE = "/api/v1";
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final HttpServer server;
    private final String url;

    private final Map<Long, Slot> slots = new LinkedHashMap<>();
    private final Map<Long, Dtos.Appointment> appointments = new LinkedHashMap<>();
    private final Map<Long, Long> patientByAppointment = new HashMap<>();
    private final Map<String, Dtos.Appointment> byKey = new HashMap<>();
    private final Map<Long, Dtos.Patient> patients = new LinkedHashMap<>();
    private final AtomicLong appointmentSequence = new AtomicLong(50);
    private final AtomicLong patientSequence = new AtomicLong(10);

    private DemoStub(HttpServer server, String url) {
        this.server = server;
        this.url = url;
    }

    /** Arranca el servidor en un puerto libre. Lo invoca {@code KioskApp} por reflexión. */
    public static DemoStub start() throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        DemoStub stub = new DemoStub(http, "http://127.0.0.1:" + http.getAddress().getPort() + BASE);
        stub.seed();
        http.createContext(BASE, stub::handle);
        http.setExecutor(null);
        http.start();
        return stub;
    }

    public String url() {
        return url;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static final List<Dtos.Location> LOCATIONS = List.of(
            new Dtos.Location(1L, "SI", "Sede San Isidro", "Av. Alfredo Salazar 350", "San Isidro"),
            new Dtos.Location(2L, "LM", "Sede La Molina", "Av. La Fontana 362", "La Molina"));

    private static final List<Dtos.Specialty> SPECIALTIES = List.of(
            new Dtos.Specialty(1L, "Cardiología", "Evaluación y control del corazón y la presión arterial.", 20),
            new Dtos.Specialty(2L, "Dermatología", "Diagnóstico y tratamiento de la piel.", 20),
            new Dtos.Specialty(3L, "Endocrinología", "Tiroides, diabetes y control hormonal.", 20),
            new Dtos.Specialty(4L, "Gastroenterología", "Sistema digestivo.", 20),
            new Dtos.Specialty(5L, "Ginecología", "Salud femenina y control preventivo.", 25),
            new Dtos.Specialty(6L, "Medicina Interna", "Evaluación general del adulto.", 20),
            new Dtos.Specialty(7L, "Neurología", "Sistema nervioso, cefaleas y mareos.", 25),
            new Dtos.Specialty(8L, "Oftalmología", "Evaluación de la vista.", 20),
            new Dtos.Specialty(9L, "Pediatría", "Atención de niños y adolescentes.", 20),
            new Dtos.Specialty(10L, "Traumatología", "Huesos, articulaciones y lesiones.", 20));

    private static final List<Dtos.Insurer> INSURERS = List.of(
            new Dtos.Insurer(1L, "Rímac Seguros", List.of(
                    new Dtos.Plan(1L, "Red Preferente", new BigDecimal("45.00")),
                    new Dtos.Plan(2L, "Red Médica", new BigDecimal("60.00")))),
            new Dtos.Insurer(2L, "Pacífico Seguros", List.of(
                    new Dtos.Plan(3L, "Plan Salud Total", new BigDecimal("50.00")),
                    new Dtos.Plan(4L, "Plan Clásico", new BigDecimal("70.00")))),
            new Dtos.Insurer(3L, "La Positiva", List.of(
                    new Dtos.Plan(5L, "Plan Integral", new BigDecimal("55.00")))),
            new Dtos.Insurer(4L, "Mapfre", List.of(
                    new Dtos.Plan(6L, "Red Nacional", new BigDecimal("65.00")))));

    private static final List<Dtos.DoctorSummary> DOCTORS = List.of(
            new Dtos.DoctorSummary(7L, "Carla Benavides Ortiz", "045712"),
            new Dtos.DoctorSummary(8L, "Álvaro Mendiola Ruiz", "038914"),
            new Dtos.DoctorSummary(9L, "Rosa Ynfantes Cárdenas", "052330"));

    private static final List<String> ROOMS = List.of("Consultorio 304", "Consultorio 212", "Consultorio 118");

    /** Un cupo de la agenda simulada. */
    private record Slot(long id, LocalDate date, LocalTime start, LocalTime end, String room,
                        Dtos.DoctorSummary doctor, long specialtyId, long locationId, boolean free) {
        Slot take() {
            return new Slot(id, date, start, end, room, doctor, specialtyId, locationId, false);
        }

        Slot release() {
            return new Slot(id, date, start, end, room, doctor, specialtyId, locationId, true);
        }

        Dtos.Slot toDto() {
            return new Dtos.Slot(id, start, end, room, doctor);
        }
    }

    private void seed() {
        patients.put(1L, new Dtos.Patient(1L, "DNI", "45871236", "Lucía Fernanda", "Paredes Quispe",
                LocalDate.of(1991, 4, 18), "987654321", "lucia.paredes@correo.pe",
                new Dtos.InsuranceSummary(3L, "Rímac Seguros", "Red Médica", "RM-00458712", "ACTIVE")));
        patients.put(2L, new Dtos.Patient(2L, "DNI", "70123456", "Diego", "Salazar Rojas",
                LocalDate.of(1988, 11, 2), "956112233", "diego.salazar@correo.pe", null));

        long id = 100;
        LocalDate today = LocalDate.now();
        for (int day = 0; day < 14; day++) {
            LocalDate date = today.plusDays(day);
            if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            for (int m = 0; m < DOCTORS.size(); m++) {
                LocalTime start = switch (m) {
                    case 0 -> LocalTime.of(7, 0);
                    case 1 -> LocalTime.of(10, 0);
                    default -> LocalTime.of(15, 0);
                };
                for (int s = 0; s < 9; s++) {
                    LocalTime from = start.plusMinutes(20L * s);
                    // Ocupación fija para que la demostración se vea igual en cada arranque.
                    boolean free = (day + m + s) % 3 != 0;
                    for (Dtos.Specialty specialty : SPECIALTIES) {
                        for (Dtos.Location location : LOCATIONS) {
                            if (specialty.id() > 3 && location.id() == 2L) {
                                continue;
                            }
                            slots.put(id, new Slot(id, date, from, from.plusMinutes(20), ROOMS.get(m),
                                    DOCTORS.get(m), specialty.id(), location.id(), free));
                            id++;
                        }
                    }
                }
            }
        }

        // Una cita ya reservada para Lucía, para que "Mis citas" tenga contenido.
        slots.values().stream()
                .filter(c -> c.specialtyId() == 1L && c.locationId() == 1L && c.free()
                        && c.date().isAfter(today.plusDays(1)))
                .findFirst()
                .ifPresent(c -> {
                    slots.put(c.id(), c.take());
                    Dtos.Appointment appointment = toAppointment(c, 1L, "Control de presión arterial");
                    appointments.put(appointment.id(), appointment);
                    patientByAppointment.put(appointment.id(), 1L);
                });
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath().substring(BASE.length());
        String method = exchange.getRequestMethod();
        Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
        try {
            if (path.equals("/locations")) {
                respond(exchange, 200, LOCATIONS);
            } else if (path.equals("/specialties")) {
                long locationId = Long.parseLong(query.getOrDefault("locationId", "1"));
                respond(exchange, 200, locationId == 2L ? SPECIALTIES.subList(0, 3) : SPECIALTIES);
            } else if (path.equals("/insurers")) {
                respond(exchange, 200, INSURERS);
            } else if (path.equals("/patients") && method.equals("GET")) {
                findPatient(exchange, query);
            } else if (path.equals("/patients") && method.equals("POST")) {
                registerPatient(exchange);
            } else if (path.startsWith("/patients/") && path.endsWith("/insurance")) {
                updateInsurance(exchange, idFrom(path, 2));
            } else if (path.startsWith("/patients/") && path.endsWith("/appointments")) {
                appointmentsOf(exchange, idFrom(path, 2));
            } else if (path.equals("/availability")) {
                availability(exchange, query);
            } else if (path.equals("/appointments") && method.equals("POST")) {
                book(exchange);
            } else if (path.endsWith("/reschedule")) {
                reschedule(exchange, idFrom(path, 2));
            } else if (path.endsWith("/cancellation")) {
                cancel(exchange, idFrom(path, 2));
            } else {
                error(exchange, 404, "RESOURCE_NOT_FOUND", "No encontramos lo que buscas.");
            }
        } catch (RuntimeException | IOException e) {
            error(exchange, 500, "INTERNAL_ERROR", "Ocurrió un problema. Inténtalo nuevamente.");
        }
    }

    private void findPatient(HttpExchange exchange, Map<String, String> query) throws IOException {
        String number = query.getOrDefault("documentNumber", "");
        Optional<Dtos.Patient> found = patients.values().stream()
                .filter(p -> p.documentNumber().equals(number))
                .findFirst();
        if (found.isPresent()) {
            respond(exchange, 200, found.get());
        } else {
            error(exchange, 404, "PATIENT_NOT_FOUND",
                    "No encontramos ese documento. Completa tus datos para registrarte.");
        }
    }

    private void registerPatient(HttpExchange exchange) throws IOException {
        Dtos.NewPatient newPatient = read(exchange, Dtos.NewPatient.class);
        boolean duplicate = patients.values().stream()
                .anyMatch(p -> p.documentNumber().equals(newPatient.documentNumber()));
        if (duplicate) {
            error(exchange, 409, "DUPLICATE_DOCUMENT",
                    "Ese documento ya está registrado. Vuelve a identificarte para continuar.");
            return;
        }
        long id = patientSequence.incrementAndGet();
        Dtos.Patient patient = new Dtos.Patient(id, newPatient.documentType(), newPatient.documentNumber(),
                newPatient.firstNames(), newPatient.lastNames(), newPatient.birthDate(), newPatient.phone(),
                newPatient.email(), null);
        patients.put(id, patient);
        respond(exchange, 201, patient);
    }

    private void updateInsurance(HttpExchange exchange, long patientId) throws IOException {
        Dtos.UpdateInsurance change = read(exchange, Dtos.UpdateInsurance.class);
        Dtos.Patient current = patients.get(patientId);
        if (current == null) {
            error(exchange, 404, "PATIENT_NOT_FOUND", "No encontramos al paciente.");
            return;
        }
        Dtos.InsuranceSummary insurance = null;
        if (change.planId() != null) {
            for (Dtos.Insurer insurer : INSURERS) {
                for (Dtos.Plan plan : insurer.plans()) {
                    if (plan.id().equals(change.planId())) {
                        insurance = new Dtos.InsuranceSummary(plan.id(), insurer.name(), plan.name(),
                                change.policyNumber(), "ACTIVE");
                    }
                }
            }
        }
        Dtos.Patient updated = new Dtos.Patient(current.id(), current.documentType(), current.documentNumber(),
                current.firstNames(), current.lastNames(), current.birthDate(), current.phone(), current.email(),
                insurance);
        patients.put(patientId, updated);
        respond(exchange, 200, updated);
    }

    private void appointmentsOf(HttpExchange exchange, long patientId) throws IOException {
        List<Dtos.Appointment> own = new ArrayList<>();
        appointments.forEach((appointmentId, appointment) -> {
            if (Long.valueOf(patientId).equals(patientByAppointment.get(appointmentId))
                    && "BOOKED".equals(appointment.status())) {
                own.add(appointment);
            }
        });
        own.sort((a, b) -> {
            int byDate = a.date().compareTo(b.date());
            return byDate != 0 ? byDate : a.startTime().compareTo(b.startTime());
        });
        respond(exchange, 200, own);
    }

    private void availability(HttpExchange exchange, Map<String, String> query) throws IOException {
        long specialtyId = Long.parseLong(query.getOrDefault("specialtyId", "1"));
        long locationId = Long.parseLong(query.getOrDefault("locationId", "1"));
        LocalDate from = LocalDate.parse(query.getOrDefault("from", LocalDate.now().toString()));
        LocalDate to = LocalDate.parse(query.getOrDefault("to", from.plusDays(13).toString()));

        List<Dtos.AvailableDay> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            final LocalDate day = date;
            List<Dtos.Slot> free = new ArrayList<>();
            slots.values().stream()
                    .filter(c -> c.date().equals(day) && c.free()
                            && c.specialtyId() == specialtyId && c.locationId() == locationId)
                    .filter(c -> !day.equals(LocalDate.now()) || c.start().isAfter(LocalTime.now()))
                    .sorted((a, b) -> a.start().compareTo(b.start()))
                    .forEach(c -> free.add(c.toDto()));
            days.add(new Dtos.AvailableDay(day, free.size(), free));
        }
        respond(exchange, 200, new Dtos.Availability(days));
    }

    private void book(HttpExchange exchange) throws IOException {
        String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
        Dtos.NewAppointment request = read(exchange, Dtos.NewAppointment.class);
        if (key != null && byKey.containsKey(key)) {
            respond(exchange, 200, byKey.get(key));
            return;
        }
        Slot slot = slots.get(request.slotId());
        if (slot == null || !slot.free()) {
            error(exchange, 409, "SLOT_NOT_AVAILABLE",
                    "Ese horario acaba de ser reservado por otra persona. Elige otro horario disponible.");
            return;
        }
        slots.put(slot.id(), slot.take());
        Dtos.Appointment appointment = toAppointment(slot, request.patientId(), request.visitReason());
        appointments.put(appointment.id(), appointment);
        patientByAppointment.put(appointment.id(), request.patientId());
        if (key != null) {
            byKey.put(key, appointment);
        }
        respond(exchange, 201, appointment);
    }

    private void reschedule(HttpExchange exchange, long appointmentId) throws IOException {
        Dtos.Reschedule request = read(exchange, Dtos.Reschedule.class);
        Dtos.Appointment current = appointments.get(appointmentId);
        Slot newSlot = slots.get(request.newSlotId());
        if (current == null) {
            error(exchange, 404, "APPOINTMENT_NOT_FOUND", "No encontramos la cita. Revisa tus citas vigentes.");
            return;
        }
        if (newSlot == null || !newSlot.free()) {
            error(exchange, 409, "SLOT_NOT_AVAILABLE",
                    "Ese horario acaba de ser reservado por otra persona. Elige otro horario disponible.");
            return;
        }
        slots.values().stream()
                .filter(c -> !c.free() && c.date().equals(current.date()) && c.start().equals(current.startTime()))
                .findFirst()
                .ifPresent(c -> slots.put(c.id(), c.release()));
        slots.put(newSlot.id(), newSlot.take());
        Dtos.Appointment appointment = new Dtos.Appointment(current.id(), current.code(), "BOOKED", newSlot.date(),
                newSlot.start(), newSlot.end(), current.specialty(), current.location(), newSlot.room(),
                newSlot.doctor().fullName(), current.coverage(), current.visitReason(),
                current.rescheduleCount() + 1, current.bookedAt());
        appointments.put(appointmentId, appointment);
        respond(exchange, 200, appointment);
    }

    private void cancel(HttpExchange exchange, long appointmentId) throws IOException {
        Dtos.Appointment current = appointments.get(appointmentId);
        if (current == null) {
            error(exchange, 404, "APPOINTMENT_NOT_FOUND", "No encontramos la cita. Revisa tus citas vigentes.");
            return;
        }
        slots.values().stream()
                .filter(c -> !c.free() && c.date().equals(current.date()) && c.start().equals(current.startTime()))
                .findFirst()
                .ifPresent(c -> slots.put(c.id(), c.release()));
        Dtos.Appointment cancelled = new Dtos.Appointment(current.id(), current.code(), "CANCELLED", current.date(),
                current.startTime(), current.endTime(), current.specialty(), current.location(), current.room(),
                current.doctor(), current.coverage(), current.visitReason(), current.rescheduleCount(),
                current.bookedAt());
        appointments.put(appointmentId, cancelled);
        respond(exchange, 200, cancelled);
    }

    private Dtos.Appointment toAppointment(Slot slot, long patientId, String visitReason) {
        Dtos.Patient patient = patients.get(patientId);
        String coverage = patient != null && patient.insurance() != null
                ? patient.insurance().description() : "Particular";
        String specialty = SPECIALTIES.stream()
                .filter(e -> e.id() == slot.specialtyId())
                .map(Dtos.Specialty::name)
                .findFirst().orElse("Cardiología");
        String location = LOCATIONS.stream()
                .filter(s -> s.id() == slot.locationId())
                .map(Dtos.Location::name)
                .findFirst().orElse("Sede San Isidro");
        return new Dtos.Appointment(appointmentSequence.incrementAndGet(), code(), "BOOKED", slot.date(), slot.start(),
                slot.end(), specialty, location, slot.room(), slot.doctor().fullName(),
                coverage, visitReason, 0, OffsetDateTime.now());
    }

    private String code() {
        StringBuilder sb = new StringBuilder("MCA-");
        long seed = appointmentSequence.get() * 7919;
        for (int i = 0; i < 5; i++) {
            sb.append(ALPHABET.charAt((int) ((seed >> (i * 5)) & 31)));
        }
        return sb.toString();
    }

    private static long idFrom(String path, int position) {
        return Long.parseLong(path.split("/")[position]);
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> values = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return values;
        }
        for (String pair : raw.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                values.put(URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
            }
        }
        return values;
    }

    private static <T> T read(HttpExchange exchange, Class<T> type) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return ApiClient.JSON.readValue(body, type);
    }

    private static void respond(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] data = ApiClient.JSON.writeValueAsBytes(body);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(data);
        }
    }

    private static void error(HttpExchange exchange, int status, String code, String message) throws IOException {
        respond(exchange, status, new Dtos.ApiError(code, message, List.of(), OffsetDateTime.now()));
    }
}
