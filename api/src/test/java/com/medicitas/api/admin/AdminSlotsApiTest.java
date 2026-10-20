package com.medicitas.api.admin;

import com.medicitas.api.ApiIntegrationTestSupport;
import com.medicitas.api.audit.AuditAction;
import com.medicitas.api.audit.AuditAction.AuditEntities;
import com.medicitas.api.audit.AuditLog;
import com.medicitas.api.audit.AuditLogRepository;
import com.medicitas.api.catalog.Doctor;
import com.medicitas.api.catalog.DoctorRepository;
import com.medicitas.api.catalog.Location;
import com.medicitas.api.catalog.LocationRepository;
import com.medicitas.api.schedule.Slot;
import com.medicitas.api.schedule.SlotStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gestión de agendas: generación de cupos sin duplicados, su reflejo en la disponibilidad del kiosko y el bloqueo
 * de cupos libres.
 */
@DisplayName("Administración de agendas")
class AdminSlotsApiTest extends ApiIntegrationTestSupport {

    private static final String SLOTS = "/api/v1/admin/slots";

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private Doctor cardiologist;
    private Doctor neurologist;
    private Location sanIsidro;
    private Location laMolina;

    @BeforeEach
    void loadCatalog() {
        List<Doctor> doctors = doctorRepository.findAllWithSpecialty();
        cardiologist = doctors.stream().filter(d -> d.getLastNames().startsWith("Benavides")).findFirst().orElseThrow();
        neurologist = doctors.stream().filter(d -> d.getLastNames().startsWith("Zapata")).findFirst().orElseThrow();
        List<Location> locations = locationRepository.findByActiveTrueOrderByIdAsc();
        sanIsidro = locations.get(0);
        laMolina = locations.get(1);
    }

    /** Dos días consecutivos tres semanas después de hoy, fuera de la agenda de demostración (14 días). */
    private LocalDate generationStart() {
        return LocalDate.now(clock).plusDays(21);
    }

    private static String weekdaysOf(LocalDate from, LocalDate to) {
        StringBuilder names = new StringBuilder();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            names.append(names.isEmpty() ? "" : ",").append('"').append(date.getDayOfWeek()).append('"');
        }
        return names.toString();
    }

    private static String generationBody(long doctorId, long locationId, LocalDate from, LocalDate to,
                                         String startTime, String endTime, Integer duration) {
        return """
                {"doctorId":%d,"locationId":%d,"room":"Consultorio 301","from":"%s","to":"%s",
                 "startTime":"%s","endTime":"%s","weekdays":[%s]%s}"""
                .formatted(doctorId, locationId, from, to, startTime, endTime, weekdaysOf(from, to),
                        duration == null ? "" : ",\"durationMinutes\":" + duration);
    }

    private ResultActions generate(String token, String body) throws Exception {
        return mockMvc.perform(post(SLOTS + "/generate").headers(bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions availability(Doctor doctor, Location location, LocalDate from, LocalDate to) throws Exception {
        return mockMvc.perform(get("/api/v1/availability")
                .param("specialtyId", doctor.getSpecialty().getId().toString())
                .param("locationId", location.getId().toString())
                .param("from", from.toString())
                .param("to", to.toString()));
    }

    @Nested
    @DisplayName("Generación")
    class Generacion {

        @Test
        @DisplayName("Genera los cupos libres, el kiosko los ve, y repetir la generación no duplica ninguno")
        void generaSinDuplicarYElKioskoLosVe() throws Exception {
            String token = loginAsAdmin();
            LocalDate from = generationStart();
            LocalDate to = from.plusDays(1);

            // 08:00 a 10:00 con la duración de la especialidad (20 min): 6 cupos por día, 2 días.
            generate(token, generationBody(cardiologist.getId(), sanIsidro.getId(), from, to, "08:00", "10:00", null))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.created").value(12))
                    .andExpect(jsonPath("$.skipped").value(0))
                    .andExpect(jsonPath("$.slots.length()").value(12))
                    .andExpect(jsonPath("$.slots[*].status", everyItem(is("FREE"))))
                    .andExpect(jsonPath("$.slots[0].startTime").value("08:00"))
                    .andExpect(jsonPath("$.slots[0].endTime").value("08:20"))
                    .andExpect(jsonPath("$.slots[0].room").value("Consultorio 301"))
                    .andExpect(jsonPath("$.slots[0].doctor.id").value(cardiologist.getId()))
                    .andExpect(jsonPath("$.slots[0].location.id").value(sanIsidro.getId()));

            availability(cardiologist, sanIsidro, from, to)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.days[0].slots[?(@.doctor.id == " + cardiologist.getId() + ")].startTime",
                            hasItems("08:00", "09:40")))
                    .andExpect(jsonPath("$.days[1].slots[?(@.doctor.id == " + cardiologist.getId() + ")]", hasSize(6)));

            // La misma generación otra vez: todo omitido.
            generate(token, generationBody(cardiologist.getId(), sanIsidro.getId(), from, to, "08:00", "10:00", null))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.created").value(0))
                    .andExpect(jsonPath("$.skipped").value(12));
            // Un horario que se cruza en parte: solo se crean los cupos de 10:00 a 11:00.
            generate(token, generationBody(cardiologist.getId(), sanIsidro.getId(), from, to, "09:00", "11:00", null))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.created").value(6))
                    .andExpect(jsonPath("$.skipped").value(6));

            List<AuditLog> entries = auditLogRepository.search(AuditEntities.SLOT, AuditAction.SLOTS_GENERATED.name(),
                    null, null, null, PageRequest.of(0, 50)).getContent();
            assertThat(entries).isNotEmpty();
            assertThat(entries).anySatisfy(entry -> assertThat(entry.getDetail())
                    .contains("12 cupos").contains(cardiologist.getFullName()).contains(sanIsidro.getName()));
        }

        @Test
        @DisplayName("Una duración distinta a la de la especialidad genera cupos de ese tamaño")
        void respetaLaDuracionIndicada() throws Exception {
            LocalDate day = generationStart().plusDays(7);

            generate(loginAsAdmin(), generationBody(cardiologist.getId(), sanIsidro.getId(), day, day, "14:00", "15:00", 30))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.created").value(2))
                    .andExpect(jsonPath("$.slots[*].startTime", hasItems("14:00", "14:30")))
                    .andExpect(jsonPath("$.slots[1].endTime").value("15:00"));
        }

        @Test
        @DisplayName("Una sede que no atiende la especialidad del médico responde 422 SPECIALTY_NOT_OFFERED")
        void sedeQueNoAtiendeLaEspecialidadResponde422() throws Exception {
            LocalDate day = generationStart();

            generate(loginAsAdmin(), generationBody(neurologist.getId(), laMolina.getId(), day, day, "08:00", "10:00", null))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("SPECIALTY_NOT_OFFERED"))
                    .andExpect(jsonPath("$.message").value(
                            "La sede Sede La Molina no atiende Neurología. Agrega la sede a la especialidad antes de generar la agenda."));
        }

        @Test
        @DisplayName("Un médico inactivo responde 422 DOCTOR_INACTIVE")
        void medicoInactivoResponde422() throws Exception {
            String token = loginAsAdmin();
            Doctor inactive = doctorRepository.save(new Doctor("I" + UUID.randomUUID().toString().substring(0, 6), null,
                    "Prueba", "Inactivo Agenda", cardiologist.getSpecialty()));
            inactive.setActive(false);
            doctorRepository.save(inactive);
            LocalDate day = generationStart();

            generate(token, generationBody(inactive.getId(), sanIsidro.getId(), day, day, "08:00", "10:00", null))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("DOCTOR_INACTIVE"));
        }

        @Test
        @DisplayName("Rango invertido, fecha pasada y hora de fin anterior a la de inicio responden 400")
        void rangosInvalidosResponden400() throws Exception {
            String token = loginAsAdmin();
            LocalDate day = generationStart();

            generate(token, generationBody(cardiologist.getId(), sanIsidro.getId(), day.plusDays(1), day, "08:00", "10:00", null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields[0].field").value("to"));
            generate(token, generationBody(cardiologist.getId(), sanIsidro.getId(), day.minusDays(30), day, "08:00", "10:00", null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields[0].field").value("from"));
            generate(token, generationBody(cardiologist.getId(), sanIsidro.getId(), day, day, "10:00", "08:00", null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields[0].field").value("endTime"));
            generate(token, generationBody(999999L, sanIsidro.getId(), day, day, "08:00", "10:00", null))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("Bloqueo")
    class Bloqueo {

        @Test
        @DisplayName("Solo un cupo libre se bloquea; bloqueado desaparece del kiosko y al desbloquearlo vuelve")
        void bloqueaSoloCuposLibres() throws Exception {
            String token = loginAsAdmin();
            Slot slot = nextFreeSlot();
            Doctor doctor = slot.getDoctor();

            mockMvc.perform(patch(SLOTS + "/" + slot.getId() + "/block").headers(bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(slot.getId()))
                    .andExpect(jsonPath("$.status").value("BLOCKED"))
                    .andExpect(jsonPath("$.startTime", matchesPattern("\\d{2}:\\d{2}")));
            assertThat(slotStatusOf(slot)).isEqualTo(SlotStatus.BLOCKED);
            availability(doctor, slot.getLocation(), slot.getDate(), slot.getDate())
                    .andExpect(jsonPath("$.days[0].slots[*].slotId", not(hasItem(slot.getId().intValue()))));

            mockMvc.perform(patch(SLOTS + "/" + slot.getId() + "/block").headers(bearer(token)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SLOT_NOT_FREE"));

            mockMvc.perform(patch(SLOTS + "/" + slot.getId() + "/unblock").headers(bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FREE"));
            assertThat(slotStatusOf(slot)).isEqualTo(SlotStatus.FREE);
            availability(doctor, slot.getLocation(), slot.getDate(), slot.getDate())
                    .andExpect(jsonPath("$.days[0].slots[*].slotId", hasItem(slot.getId().intValue())));
            mockMvc.perform(patch(SLOTS + "/" + slot.getId() + "/unblock").headers(bearer(token)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SLOT_NOT_BLOCKED"));

            List<AuditLog> entries = auditLogRepository.findByEntityAndEntityIdOrderByIdAsc(AuditEntities.SLOT, slot.getId());
            assertThat(entries).extracting(AuditLog::getAction)
                    .containsExactly(AuditAction.SLOT_BLOCKED.name(), AuditAction.SLOT_UNBLOCKED.name());
        }

        @Test
        @DisplayName("Un cupo con cita reservada no se puede bloquear: 409 SLOT_NOT_FREE")
        void cupoReservadoNoSeBloquea() throws Exception {
            long patientId = registerPatient();
            Slot slot = nextFreeSlot();
            book(patientId, slot.getId(), UUID.randomUUID()).andExpect(status().isCreated());

            mockMvc.perform(patch(SLOTS + "/" + slot.getId() + "/block").headers(bearer(loginAsAdmin())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SLOT_NOT_FREE"))
                    .andExpect(jsonPath("$.message").value(
                            "Este cupo tiene una cita reservada; no se puede bloquear. Reprograma o anula la cita primero."));
            assertThat(slotStatusOf(slot)).isEqualTo(SlotStatus.BOOKED);
        }

        @Test
        @DisplayName("Un cupo inexistente responde 404")
        void cupoInexistenteResponde404() throws Exception {
            mockMvc.perform(patch(SLOTS + "/999999/block").headers(bearer(loginAsAdmin())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("Consulta y acceso")
    class ConsultaYAcceso {

        @Test
        @DisplayName("GET /admin/slots lista los cupos del médico en el rango con su estado")
        void listaLosCuposDelMedico() throws Exception {
            String token = loginAsAdmin();
            Slot slot = nextFreeSlot(candidate -> candidate.getDoctor().getId().equals(cardiologist.getId()));
            mockMvc.perform(patch(SLOTS + "/" + slot.getId() + "/block").headers(bearer(token))).andExpect(status().isOk());

            mockMvc.perform(get(SLOTS).headers(bearer(token))
                            .param("doctorId", cardiologist.getId().toString())
                            .param("from", slot.getDate().toString())
                            .param("to", slot.getDate().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id", hasItem(slot.getId().intValue())))
                    .andExpect(jsonPath("$[?(@.id == " + slot.getId() + ")].status").value(hasItem("BLOCKED")))
                    .andExpect(jsonPath("$[*].doctor.id", everyItem(is(cardiologist.getId().intValue()))));
            mockMvc.perform(get(SLOTS).headers(bearer(token))
                            .param("doctorId", cardiologist.getId().toString())
                            .param("from", slot.getDate().toString())
                            .param("to", slot.getDate().minusDays(1).toString()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields[0].field").value("to"));
        }

        @Test
        @DisplayName("Admisión no puede generar ni bloquear cupos: 403")
        void admisionRecibe403() throws Exception {
            String token = loginAsFrontDesk();
            LocalDate day = generationStart();

            generate(token, generationBody(cardiologist.getId(), sanIsidro.getId(), day, day, "08:00", "10:00", null))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            mockMvc.perform(patch(SLOTS + "/1/block").headers(bearer(token)))
                    .andExpect(status().isForbidden());
        }
    }
}
