package com.medicitas.api.admin;

import com.medicitas.api.ApiIntegrationTestSupport;
import com.medicitas.api.audit.AuditAction;
import com.medicitas.api.audit.AuditAction.AuditEntities;
import com.medicitas.api.audit.AuditLog;
import com.medicitas.api.audit.AuditLogRepository;
import com.medicitas.api.catalog.Location;
import com.medicitas.api.catalog.LocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Administración de especialidades y médicos, y su reflejo inmediato en lo que ve el kiosko
 * ({@code GET /specialties} y {@code GET /availability}).
 */
@DisplayName("Administración del catálogo")
class AdminCatalogApiTest extends ApiIntegrationTestSupport {

    private static final String ADMIN = "/api/v1/admin";

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private Location sanIsidro;
    private Location laMolina;

    @BeforeEach
    void loadLocations() {
        List<Location> locations = locationRepository.findByActiveTrueOrderByIdAsc();
        sanIsidro = locations.get(0);
        laMolina = locations.get(1);
    }

    private static String unique(String prefix) {
        return prefix + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    private ResultActions postJson(String token, String path, String body) throws Exception {
        return mockMvc.perform(post(ADMIN + path).headers(bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions putJson(String token, String path, String body) throws Exception {
        return mockMvc.perform(put(ADMIN + path).headers(bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String specialtyBody(String name, int minutes, String locationIds, String active) {
        return """
                {"name":"%s","description":"Especialidad de prueba","appointmentMinutes":%d,"locationIds":[%s]%s}"""
                .formatted(name, minutes, locationIds, active == null ? "" : ",\"active\":" + active);
    }

    private long createSpecialty(String token, String name, String locationIds) throws Exception {
        MvcResult result = postJson(token, "/specialties", specialtyBody(name, 30, locationIds, null))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    private static String doctorBody(String license, String lastNames, long specialtyId, String active) {
        return """
                {"license":"%s","specialistRegistry":null,"firstNames":"Prueba","lastNames":"%s","specialtyId":%d%s}"""
                .formatted(license, lastNames, specialtyId, active == null ? "" : ",\"active\":" + active);
    }

    private long createDoctor(String token, long specialtyId) throws Exception {
        MvcResult result = postJson(token, "/doctors",
                doctorBody("P" + UUID.randomUUID().toString().substring(0, 6), unique("Médico"), specialtyId, null))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    /** Un día dentro de un mes, nunca domingo, fuera de la agenda de demostración. */
    private LocalDate agendaDay() {
        LocalDate day = LocalDate.now(clock).plusDays(30);
        return day.getDayOfWeek() == DayOfWeek.SUNDAY ? day.plusDays(1) : day;
    }

    private void generateSlots(String token, long doctorId, long locationId, LocalDate day) throws Exception {
        postJson(token, "/slots/generate", """
                {"doctorId":%d,"locationId":%d,"room":"Consultorio 9","from":"%s","to":"%s",
                 "startTime":"08:00","endTime":"09:00","weekdays":["%s"]}"""
                .formatted(doctorId, locationId, day, day, day.getDayOfWeek()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(2));
    }

    private ResultActions availability(long specialtyId, long locationId, LocalDate day) throws Exception {
        return mockMvc.perform(get("/api/v1/availability")
                .param("specialtyId", String.valueOf(specialtyId))
                .param("locationId", String.valueOf(locationId))
                .param("from", day.toString())
                .param("to", day.toString()));
    }

    @Nested
    @DisplayName("Especialidades")
    class Especialidades {

        @Test
        @DisplayName("Crear una especialidad la muestra en el kiosko solo en las sedes indicadas")
        void crearEspecialidadLaMuestraEnElKiosko() throws Exception {
            String token = loginAsAdmin();
            String name = unique("Reumatología");

            MvcResult result = postJson(token, "/specialties", specialtyBody(name, 30, sanIsidro.getId().toString(), null))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value(name))
                    .andExpect(jsonPath("$.appointmentMinutes").value(30))
                    .andExpect(jsonPath("$.active").value(true))
                    .andExpect(jsonPath("$.locations.length()").value(1))
                    .andExpect(jsonPath("$.locations[0].id").value(sanIsidro.getId()))
                    .andReturn();
            long id = readId(result);

            mockMvc.perform(get("/api/v1/specialties"))
                    .andExpect(jsonPath("$[*].name", hasItem(name)));
            mockMvc.perform(get("/api/v1/specialties").param("locationId", sanIsidro.getId().toString()))
                    .andExpect(jsonPath("$[*].name", hasItem(name)));
            mockMvc.perform(get("/api/v1/specialties").param("locationId", laMolina.getId().toString()))
                    .andExpect(jsonPath("$[*].name", not(hasItem(name))));
            mockMvc.perform(get(ADMIN + "/specialties").headers(bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id == " + id + ")].locations[0].code").value(hasItem("SI")));
            List<AuditLog> entries = auditLogRepository.findByEntityAndEntityIdOrderByIdAsc(AuditEntities.SPECIALTY, id);
            assertThat(entries).extracting(AuditLog::getAction).containsExactly(AuditAction.SPECIALTY_CREATED.name());
            assertThat(entries.getFirst().getDetail()).contains(name).contains("Sede San Isidro");
        }

        @Test
        @DisplayName("Un nombre repetido, aunque cambie de mayúsculas, responde 409 SPECIALTY_NAME_TAKEN")
        void nombreRepetidoResponde409() throws Exception {
            String token = loginAsAdmin();
            String name = unique("Alergología");
            long id = createSpecialty(token, name, sanIsidro.getId().toString());

            postJson(token, "/specialties", specialtyBody(name.toUpperCase(), 20, sanIsidro.getId().toString(), null))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SPECIALTY_NAME_TAKEN"));
            // Editar la misma especialidad con su propio nombre sí se permite.
            putJson(token, "/specialties/" + id, specialtyBody(name, 25, sanIsidro.getId().toString(), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.appointmentMinutes").value(25));
        }

        @Test
        @DisplayName("Una sede inexistente responde 404 y una duración inválida responde 400")
        void datosInvalidos() throws Exception {
            String token = loginAsAdmin();

            postJson(token, "/specialties", specialtyBody(unique("Sede Fantasma"), 20, "999999", null))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
            postJson(token, "/specialties", specialtyBody(unique("Sin Minutos"), 0, sanIsidro.getId().toString(), null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields[0].field").value("appointmentMinutes"));
            putJson(token, "/specialties/999999", specialtyBody(unique("Nadie"), 20, sanIsidro.getId().toString(), null))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Desactivar una especialidad o quitarle una sede la saca de /specialties y de /availability del kiosko")
        void desactivarLaSacaDelKiosko() throws Exception {
            String token = loginAsAdmin();
            String name = unique("Nutrición");
            String bothLocations = sanIsidro.getId() + "," + laMolina.getId();
            long specialtyId = createSpecialty(token, name, bothLocations);
            long doctorId = createDoctor(token, specialtyId);
            LocalDate day = agendaDay();
            generateSlots(token, doctorId, sanIsidro.getId(), day);
            availability(specialtyId, sanIsidro.getId(), day)
                    .andExpect(jsonPath("$.days[0].freeSlots").value(2))
                    .andExpect(jsonPath("$.days[0].slots[*].doctor.id", hasItem((int) doctorId)));

            // 1. Especialidad inactiva: desaparece del listado y la disponibilidad queda vacía.
            putJson(token, "/specialties/" + specialtyId, specialtyBody(name, 30, bothLocations, "false"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(false));
            mockMvc.perform(get("/api/v1/specialties"))
                    .andExpect(jsonPath("$[*].name", not(hasItem(name))));
            availability(specialtyId, sanIsidro.getId(), day)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.days.length()").value(1))
                    .andExpect(jsonPath("$.days[0].freeSlots").value(0));
            mockMvc.perform(get(ADMIN + "/specialties").headers(bearer(token)))
                    .andExpect(jsonPath("$[?(@.id == " + specialtyId + ")].active").value(hasItem(false)));

            // 2. Especialidad activa pero sin la sede San Isidro: solo se ofrece en La Molina.
            putJson(token, "/specialties/" + specialtyId, specialtyBody(name, 30, laMolina.getId().toString(), "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.locations.length()").value(1))
                    .andExpect(jsonPath("$.locations[0].id").value(laMolina.getId()));
            mockMvc.perform(get("/api/v1/specialties").param("locationId", sanIsidro.getId().toString()))
                    .andExpect(jsonPath("$[*].name", not(hasItem(name))));
            mockMvc.perform(get("/api/v1/specialties").param("locationId", laMolina.getId().toString()))
                    .andExpect(jsonPath("$[*].name", hasItem(name)));
            availability(specialtyId, sanIsidro.getId(), day)
                    .andExpect(jsonPath("$.days[0].freeSlots").value(0));

            // 3. Vuelve San Isidro: los cupos reaparecen sin regenerarlos.
            putJson(token, "/specialties/" + specialtyId, specialtyBody(name, 30, bothLocations, "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.locations.length()").value(2));
            availability(specialtyId, sanIsidro.getId(), day)
                    .andExpect(jsonPath("$.days[0].freeSlots").value(2));
            List<AuditLog> entries = auditLogRepository.findByEntityAndEntityIdOrderByIdAsc(AuditEntities.SPECIALTY, specialtyId);
            assertThat(entries).extracting(AuditLog::getAction).containsExactly(
                    AuditAction.SPECIALTY_CREATED.name(), AuditAction.SPECIALTY_UPDATED.name(),
                    AuditAction.SPECIALTY_UPDATED.name(), AuditAction.SPECIALTY_UPDATED.name());
        }
    }

    @Nested
    @DisplayName("Médicos")
    class Medicos {

        @Test
        @DisplayName("Crear un médico lo lista con su especialidad y queda auditado")
        void crearMedico() throws Exception {
            String token = loginAsAdmin();
            long specialtyId = createSpecialty(token, unique("Geriatría"), sanIsidro.getId().toString());
            String license = "L" + UUID.randomUUID().toString().substring(0, 6);

            MvcResult result = postJson(token, "/doctors", doctorBody(license, "Quiroga Paz", specialtyId, null))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.license").value(license))
                    .andExpect(jsonPath("$.fullName").value("Prueba Quiroga Paz"))
                    .andExpect(jsonPath("$.specialty.id").value(specialtyId))
                    .andExpect(jsonPath("$.active").value(true))
                    .andReturn();
            long id = readId(result);

            mockMvc.perform(get(ADMIN + "/doctors").headers(bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].license", hasItem(license)));
            List<AuditLog> entries = auditLogRepository.findByEntityAndEntityIdOrderByIdAsc(AuditEntities.DOCTOR, id);
            assertThat(entries).extracting(AuditLog::getAction).containsExactly(AuditAction.DOCTOR_CREATED.name());
        }

        @Test
        @DisplayName("Una colegiatura repetida responde 409 DOCTOR_LICENSE_TAKEN y una especialidad inexistente 404")
        void licenciaRepetidaYEspecialidadInexistente() throws Exception {
            String token = loginAsAdmin();
            long specialtyId = createSpecialty(token, unique("Psiquiatría"), sanIsidro.getId().toString());
            String license = "R" + UUID.randomUUID().toString().substring(0, 6);
            postJson(token, "/doctors", doctorBody(license, "Uno Uno", specialtyId, null)).andExpect(status().isCreated());

            postJson(token, "/doctors", doctorBody(license.toLowerCase(), "Dos Dos", specialtyId, null))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DOCTOR_LICENSE_TAKEN"));
            postJson(token, "/doctors", doctorBody("Z" + UUID.randomUUID().toString().substring(0, 6), "Tres", 999999, null))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }

        @Test
        @DisplayName("Desactivar un médico oculta sus cupos en la disponibilidad del kiosko")
        void desactivarMedicoOcultaSusCupos() throws Exception {
            String token = loginAsAdmin();
            long specialtyId = createSpecialty(token, unique("Neumología"), sanIsidro.getId().toString());
            long doctorId = createDoctor(token, specialtyId);
            LocalDate day = agendaDay();
            generateSlots(token, doctorId, sanIsidro.getId(), day);
            availability(specialtyId, sanIsidro.getId(), day)
                    .andExpect(jsonPath("$.days[0].freeSlots").value(2));

            putJson(token, "/doctors/" + doctorId,
                    doctorBody("N" + UUID.randomUUID().toString().substring(0, 6), "Inactivo Prueba", specialtyId, "false"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(false))
                    .andExpect(jsonPath("$.lastNames").value("Inactivo Prueba"));

            availability(specialtyId, sanIsidro.getId(), day)
                    .andExpect(jsonPath("$.days[0].freeSlots").value(0));
            mockMvc.perform(get(ADMIN + "/doctors").headers(bearer(token)))
                    .andExpect(jsonPath("$[?(@.id == " + doctorId + ")].active").value(hasItem(false)));
            List<AuditLog> entries = auditLogRepository.findByEntityAndEntityIdOrderByIdAsc(AuditEntities.DOCTOR, doctorId);
            assertThat(entries).extracting(AuditLog::getAction)
                    .containsExactly(AuditAction.DOCTOR_CREATED.name(), AuditAction.DOCTOR_UPDATED.name());
            assertThat(entries.getLast().getDetail()).contains("inactivo");
        }
    }

    @Nested
    @DisplayName("Sedes y acceso")
    class SedesYAcceso {

        @Test
        @DisplayName("GET /admin/locations lista las sedes con su estado")
        void listaLasSedes() throws Exception {
            mockMvc.perform(get(ADMIN + "/locations").headers(bearer(loginAsAdmin())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].code", hasItem("SI")))
                    .andExpect(jsonPath("$[*].code", hasItem("LM")))
                    .andExpect(jsonPath("$[0].active").value(true));
        }

        @Test
        @DisplayName("Admisión y médicos reciben 403 en el catálogo de administración")
        void otrosRolesReciben403() throws Exception {
            mockMvc.perform(get(ADMIN + "/specialties").headers(bearer(loginAsFrontDesk())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            postJson(loginAsFrontDesk(), "/doctors", doctorBody("X123", "Nadie", 1, null))
                    .andExpect(status().isForbidden());
        }
    }
}
