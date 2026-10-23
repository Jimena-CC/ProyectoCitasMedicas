package com.medicitas.api;

import com.jayway.jsonpath.JsonPath;
import com.medicitas.api.demo.DemoDataLoader;
import com.medicitas.api.schedule.Slot;
import com.medicitas.api.schedule.SlotRepository;
import com.medicitas.api.schedule.SlotStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base de las pruebas de integración: todas comparten un contexto con la base H2 y los datos de demostración.
 * Cada prueba registra sus propios pacientes y usa cupos que ninguna otra prueba tomó.
 * Los endpoints del personal se prueban con {@link #loginAs(String, String)} y {@link #bearer(String)}.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class ApiIntegrationTestSupport {

    private static final AtomicInteger NEXT_DNI = new AtomicInteger(80_000_000);
    private static final Set<Long> USED_SLOTS = ConcurrentHashMap.newKeySet();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected SlotRepository slotRepository;

    @Autowired
    protected Clock clock;

    protected long registerPatient() throws Exception {
        String dni = String.valueOf(NEXT_DNI.getAndIncrement());
        String body = """
                {"documentType":"DNI","documentNumber":"%s","firstNames":"Prueba","lastNames":"Kiosko Integración",
                 "birthDate":"1990-05-10","sex":"F","phone":"912345678",
                 "email":"prueba%s@demo.medicitas.pe","acceptsNotifications":true}""".formatted(dni, dni);
        MvcResult result = mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    protected ResultActions book(long patientId, long slotId, UUID idempotencyKey) throws Exception {
        return book(patientId, slotId, idempotencyKey, null);
    }

    /**
     * Reserva un cupo; con {@code token} la reserva se atribuye al usuario del personal (canal FRONT_DESK).
     *
     * @param patientId      paciente
     * @param slotId         cupo libre
     * @param idempotencyKey clave del intento
     * @param token          JWT del personal o {@code null} para simular el kiosko
     * @return la respuesta para encadenar aserciones
     */
    protected ResultActions book(long patientId, long slotId, UUID idempotencyKey, String token) throws Exception {
        String body = """
                {"patientId":%d,"slotId":%d,"visitReason":"Dolor de cabeza frecuente"}"""
                .formatted(patientId, slotId);
        HttpHeaders headers = token == null ? new HttpHeaders() : bearer(token);
        return mockMvc.perform(post("/api/v1/appointments")
                .headers(headers)
                .header("Idempotency-Key", idempotencyKey.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /**
     * Inicia sesión por la API y devuelve el token JWT.
     *
     * @param username usuario de demostración, por ejemplo {@link DemoDataLoader#ADMIN_USERNAME}
     * @param password su contraseña
     * @return el token para la cabecera {@code Authorization}
     */
    protected String loginAs(String username, String password) throws Exception {
        String body = """
                {"username":"%s","password":"%s"}""".formatted(username, password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.token");
    }

    protected String loginAsAdmin() throws Exception {
        return loginAs(DemoDataLoader.ADMIN_USERNAME, DemoDataLoader.ADMIN_PASSWORD);
    }

    protected String loginAsFrontDesk() throws Exception {
        return loginAs(DemoDataLoader.FRONT_DESK_USERNAME, DemoDataLoader.FRONT_DESK_PASSWORD);
    }

    /**
     * Cabecera {@code Authorization} con el token.
     *
     * @param token JWT devuelto por {@link #loginAs(String, String)}
     * @return la cabecera lista para {@code .headers(...)}
     */
    protected static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    protected static long readId(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        return id.longValue();
    }

    protected Slot nextFreeSlot() {
        return nextFreeSlot(slot -> true);
    }

    protected Slot nextFreeSlot(Predicate<Slot> filter) {
        for (Slot slot : futureFreeSlots()) {
            if (filter.test(slot) && USED_SLOTS.add(slot.getId())) {
                return slot;
            }
        }
        throw new IllegalStateException("No quedan cupos libres para la prueba");
    }

    protected Slot freeSlotOfTheSameSpecialty(Slot base) {
        Long specialtyId = base.getDoctor().getSpecialty().getId();
        return nextFreeSlot(slot -> slot.getDoctor().getSpecialty().getId().equals(specialtyId));
    }

    /**
     * Dos cupos libres de médicos distintos con la misma fecha y hora de inicio.
     */
    protected List<Slot> twoSlotsAtTheSameTime() {
        Map<String, List<Slot>> byTime = futureFreeSlots().stream()
                .filter(slot -> !USED_SLOTS.contains(slot.getId()))
                .collect(Collectors.groupingBy(slot -> slot.getDate() + "T" + slot.getStartTime()));
        List<Slot> pair = byTime.values().stream()
                .filter(slots -> slots.size() >= 2)
                .findFirst()
                .map(slots -> slots.subList(0, 2))
                .orElseThrow(() -> new IllegalStateException("No hay dos cupos libres en el mismo horario"));
        pair.forEach(slot -> USED_SLOTS.add(slot.getId()));
        return pair;
    }

    protected SlotStatus slotStatusOf(Slot slot) {
        return Objects.requireNonNull(slotRepository.findStatusById(slot.getId()));
    }

    private List<Slot> futureFreeSlots() {
        return slotRepository.findWithDetailByStatusAfter(SlotStatus.FREE, LocalDate.now(clock));
    }
}
