package com.medicitas.api.frontdesk;

import com.jayway.jsonpath.JsonPath;
import com.medicitas.api.ApiIntegrationTestSupport;
import com.medicitas.api.appointment.Appointment;
import com.medicitas.api.appointment.AppointmentHistory;
import com.medicitas.api.appointment.AppointmentHistoryRepository;
import com.medicitas.api.appointment.AppointmentRepository;
import com.medicitas.api.appointment.BookingChannel;
import com.medicitas.api.audit.AuditAction;
import com.medicitas.api.audit.AuditAction.AuditEntities;
import com.medicitas.api.audit.AuditLog;
import com.medicitas.api.audit.AuditLogRepository;
import com.medicitas.api.catalog.InsurancePlan;
import com.medicitas.api.catalog.InsurancePlanRepository;
import com.medicitas.api.demo.DemoDataLoader;
import com.medicitas.api.patient.CoverageStatus;
import com.medicitas.api.patient.DocumentType;
import com.medicitas.api.patient.Patient;
import com.medicitas.api.patient.PatientInsurance;
import com.medicitas.api.patient.PatientInsuranceRepository;
import com.medicitas.api.patient.PatientRepository;
import com.medicitas.api.schedule.Slot;
import com.medicitas.api.schedule.SlotStatus;
import com.medicitas.api.security.AppUser;
import com.medicitas.api.security.AppUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Operaciones de ventanilla por la API: pacientes, validación de seguro y citas con canal FRONT_DESK.
 */
@DisplayName("API de admisión")
class FrontDeskApiTest extends ApiIntegrationTestSupport {

    private static final String DOCTOR_USERNAME = DemoDataLoader.DOCTOR_USERNAME_PREFIX + "benavides";
    private static final String LUCIA_DNI = "45871236";
    private static final AtomicInteger NEXT_DNI = new AtomicInteger(81_000_000);

    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private AppointmentHistoryRepository appointmentHistoryRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private PatientInsuranceRepository patientInsuranceRepository;
    @Autowired
    private InsurancePlanRepository insurancePlanRepository;

    private ResultActions frontDeskBook(String token, long patientId, long slotId, UUID key) throws Exception {
        return mockMvc.perform(post("/api/v1/front-desk/appointments")
                .headers(bearer(token))
                .header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"patientId":%d,"slotId":%d,"visitReason":"Control anual"}""".formatted(patientId, slotId)));
    }

    private static String patientBody(String dni) {
        return """
                {"documentType":"DNI","documentNumber":"%s","firstNames":"Prueba","lastNames":"Ventanilla",
                 "birthDate":"1985-03-20","sex":"M","phone":"912345678","email":"ventanilla%s@demo.medicitas.pe"}"""
                .formatted(dni, dni);
    }

    @Nested
    @DisplayName("Pacientes")
    class Pacientes {

        @Test
        @DisplayName("Busca a un paciente existente por documento y devuelve su seguro vigente")
        void buscaUnPacienteExistente() throws Exception {
            String token = loginAsFrontDesk();

            mockMvc.perform(get("/api/v1/front-desk/patients").headers(bearer(token))
                            .param("documentType", "DNI").param("documentNumber", LUCIA_DNI))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lastNames").value("Paredes Quispe"))
                    .andExpect(jsonPath("$.insurance.coverageStatus").value("ACTIVE"));
        }

        @Test
        @DisplayName("Un documento inexistente responde 404 PATIENT_NOT_FOUND")
        void documentoInexistenteResponde404() throws Exception {
            String token = loginAsFrontDesk();

            mockMvc.perform(get("/api/v1/front-desk/patients").headers(bearer(token))
                            .param("documentType", "DNI").param("documentNumber", "11111111"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
        }

        @Test
        @DisplayName("Registra un paciente con las validaciones del kiosko y rechaza el documento duplicado")
        void registraUnPacienteYRechazaElDuplicado() throws Exception {
            String token = loginAsFrontDesk();
            String dni = String.valueOf(NEXT_DNI.getAndIncrement());

            MvcResult created = mockMvc.perform(post("/api/v1/front-desk/patients").headers(bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(patientBody(dni)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", startsWith("/api/v1/front-desk/patients/")))
                    .andExpect(jsonPath("$.documentNumber").value(dni))
                    .andExpect(jsonPath("$.insurance").isEmpty())
                    .andReturn();
            assertThat(patientRepository.existsById(readId(created))).isTrue();

            mockMvc.perform(post("/api/v1/front-desk/patients").headers(bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(patientBody(dni)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_DOCUMENT"));
            mockMvc.perform(post("/api/v1/front-desk/patients").headers(bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patientBody("123")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_DATA"))
                    .andExpect(jsonPath("$.fields[0].field").value("documentNumber"));
        }
    }

    @Nested
    @DisplayName("Validación de aseguradora")
    class ValidacionDeAseguradora {

        @Test
        @DisplayName("Un paciente con cobertura activa y vigente es válido")
        void coberturaVigenteEsValida() throws Exception {
            String token = loginAsFrontDesk();
            Patient lucia = patientRepository.findByDocumentTypeAndDocumentNumber(DocumentType.DNI, LUCIA_DNI).orElseThrow();

            mockMvc.perform(get("/api/v1/front-desk/patients/" + lucia.getId() + "/insurance").headers(bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.patientId").value(lucia.getId()))
                    .andExpect(jsonPath("$.valid").value(true))
                    .andExpect(jsonPath("$.message").value(
                            "El paciente tiene una cobertura vigente. Puedes reservar con ese seguro."))
                    .andExpect(jsonPath("$.coverages[0].valid").value(true))
                    .andExpect(jsonPath("$.coverages[0].insurer").value("Rímac Seguros"))
                    .andExpect(jsonPath("$.coverages[0].plan").value("Red Médica"))
                    .andExpect(jsonPath("$.coverages[0].policyNumber").value("RM-00458712"))
                    .andExpect(jsonPath("$.coverages[0].coverageStatus").value("ACTIVE"))
                    .andExpect(jsonPath("$.coverages[0].validUntil").value(LocalDate.now(clock).plusYears(1).toString()));
        }

        @Test
        @DisplayName("Sin seguro registrado indica que puede atenderse como particular")
        void sinSeguroIndicaParticular() throws Exception {
            String token = loginAsFrontDesk();
            long patientId = registerPatient();

            mockMvc.perform(get("/api/v1/front-desk/patients/" + patientId + "/insurance").headers(bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(false))
                    .andExpect(jsonPath("$.message").value(
                            "El paciente no tiene ningún seguro registrado. Puede atenderse como particular o asociar un seguro."))
                    .andExpect(jsonPath("$.coverages").isEmpty());
        }

        @Test
        @DisplayName("Una cobertura suspendida o vencida no es válida y se sugiere atención particular")
        void coberturaSuspendidaOVencidaNoEsValida() throws Exception {
            String token = loginAsFrontDesk();
            Patient patient = patientRepository.findById(registerPatient()).orElseThrow();
            List<InsurancePlan> plans = insurancePlanRepository.findAll();
            patientInsuranceRepository.save(new PatientInsurance(patient, plans.get(0), "S-1",
                    CoverageStatus.SUSPENDED, null));
            patientInsuranceRepository.save(new PatientInsurance(patient, plans.get(1), "V-1",
                    CoverageStatus.ACTIVE, LocalDate.now(clock).minusDays(1)));

            mockMvc.perform(get("/api/v1/front-desk/patients/" + patient.getId() + "/insurance").headers(bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(false))
                    .andExpect(jsonPath("$.message").value(
                            "El paciente no tiene una cobertura vigente. Puede atenderse como particular o actualizar su seguro."))
                    .andExpect(jsonPath("$.coverages.length()").value(2))
                    .andExpect(jsonPath("$.coverages[*].valid").value(everyItem(is(false))));
        }

        @Test
        @DisplayName("Asociar un plan desde ventanilla deja la cobertura vigente y válida")
        void asociarUnPlanDejaLaCoberturaValida() throws Exception {
            String token = loginAsFrontDesk();
            long patientId = registerPatient();
            InsurancePlan plan = insurancePlanRepository.findAll().getFirst();

            mockMvc.perform(put("/api/v1/front-desk/patients/" + patientId + "/insurance").headers(bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"planId":%d,"policyNumber":"POL-123"}""".formatted(plan.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.insurance.plan").value(plan.getName()))
                    .andExpect(jsonPath("$.insurance.coverageStatus").value("ACTIVE"));
            mockMvc.perform(get("/api/v1/front-desk/patients/" + patientId + "/insurance").headers(bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(true))
                    .andExpect(jsonPath("$.coverages[0].policyNumber").value("POL-123"));
        }

        @Test
        @DisplayName("Un paciente inexistente responde 404 PATIENT_NOT_FOUND")
        void pacienteInexistenteResponde404() throws Exception {
            String token = loginAsFrontDesk();

            mockMvc.perform(get("/api/v1/front-desk/patients/999999/insurance").headers(bearer(token)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("Citas")
    class Citas {

        @Test
        @DisplayName("Reservar desde ventanilla crea la cita con canal FRONT_DESK atribuida al usuario de admisión")
        void reservarCreaLaCitaConCanalFrontDesk() throws Exception {
            String token = loginAsFrontDesk();
            AppUser frontDesk = appUserRepository.findByUsernameIgnoreCase(DemoDataLoader.FRONT_DESK_USERNAME).orElseThrow();
            long patientId = registerPatient();
            Slot slot = nextFreeSlot();
            UUID key = UUID.randomUUID();

            MvcResult created = frontDeskBook(token, patientId, slot.getId(), key)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("BOOKED"))
                    .andExpect(jsonPath("$.date").value(slot.getDate().toString()))
                    .andExpect(jsonPath("$.coverage").value("Particular"))
                    .andReturn();
            long appointmentId = readId(created);

            Appointment appointment = appointmentRepository.findById(appointmentId).orElseThrow();
            List<AuditLog> audit = auditLogRepository
                    .findByEntityAndEntityIdOrderByIdAsc(AuditEntities.APPOINTMENT, appointmentId);
            List<AppointmentHistory> history = appointmentHistoryRepository.findByAppointmentIdOrderByIdAsc(appointmentId);
            assertAll(
                    () -> assertThat(appointment.getBookingChannel()).isEqualTo(BookingChannel.FRONT_DESK),
                    () -> assertThat(slotStatusOf(slot)).isEqualTo(SlotStatus.BOOKED),
                    () -> assertThat(audit).hasSize(1),
                    () -> assertThat(audit.getFirst().getAction()).isEqualTo(AuditAction.APPOINTMENT_BOOKED.name()),
                    () -> assertThat(audit.getFirst().getUser().getUsername()).isEqualTo(DemoDataLoader.FRONT_DESK_USERNAME),
                    () -> assertThat(audit.getFirst().getDetail()).contains("por admisión"),
                    () -> assertThat(history).hasSize(1),
                    () -> assertThat(history.getFirst().getUser().getId()).isEqualTo(frontDesk.getId()));

            // Reintento con la misma clave: misma cita, 200.
            assertThat(readId(frontDeskBook(token, patientId, slot.getId(), key)
                    .andExpect(status().isOk()).andReturn())).isEqualTo(appointmentId);
            mockMvc.perform(get("/api/v1/front-desk/patients/" + patientId + "/appointments").headers(bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(appointmentId));
        }

        @Test
        @DisplayName("No se puede reservar un cupo ya tomado: 409 SLOT_NOT_AVAILABLE (RF-07)")
        void noSePuedeReservarUnCupoTomado() throws Exception {
            String token = loginAsFrontDesk();
            long kioskPatient = registerPatient();
            long frontDeskPatient = registerPatient();
            Slot slot = nextFreeSlot();
            book(kioskPatient, slot.getId(), UUID.randomUUID()).andExpect(status().isCreated());

            frontDeskBook(token, frontDeskPatient, slot.getId(), UUID.randomUUID())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SLOT_NOT_AVAILABLE"));
        }

        @Test
        @DisplayName("No se puede duplicar una cita del paciente en el mismo horario: 409 DUPLICATE_APPOINTMENT (RF-08)")
        void noSePuedeDuplicarUnaCitaEnElMismoHorario() throws Exception {
            String token = loginAsFrontDesk();
            long patientId = registerPatient();
            List<Slot> sameTime = twoSlotsAtTheSameTime();

            frontDeskBook(token, patientId, sameTime.get(0).getId(), UUID.randomUUID()).andExpect(status().isCreated());
            frontDeskBook(token, patientId, sameTime.get(1).getId(), UUID.randomUUID())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_APPOINTMENT"));

            assertThat(slotStatusOf(sameTime.get(1))).isEqualTo(SlotStatus.FREE);
        }

        @Test
        @DisplayName("Reservar sin Idempotency-Key responde 400 INVALID_DATA")
        void reservarSinIdempotencyKeyResponde400() throws Exception {
            String token = loginAsFrontDesk();

            mockMvc.perform(post("/api/v1/front-desk/appointments").headers(bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"patientId\":1,\"slotId\":1,\"visitReason\":\"Control\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_DATA"))
                    .andExpect(jsonPath("$.fields[0].field").value("Idempotency-Key"));
        }

        @Test
        @DisplayName("Reprogramar desde ventanilla libera el cupo original y queda atribuido al usuario")
        void reprogramarLiberaElCupoOriginal() throws Exception {
            String token = loginAsFrontDesk();
            long patientId = registerPatient();
            Slot original = nextFreeSlot();
            Slot newSlot = freeSlotOfTheSameSpecialty(original);
            long appointmentId = readId(frontDeskBook(token, patientId, original.getId(), UUID.randomUUID())
                    .andExpect(status().isCreated()).andReturn());

            mockMvc.perform(patch("/api/v1/front-desk/appointments/" + appointmentId + "/reschedule")
                            .headers(bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"newSlotId\":" + newSlot.getId() + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.date").value(newSlot.getDate().toString()))
                    .andExpect(jsonPath("$.rescheduleCount").value(1));

            assertThat(slotStatusOf(original)).isEqualTo(SlotStatus.FREE);
            assertThat(slotStatusOf(newSlot)).isEqualTo(SlotStatus.BOOKED);
            List<AuditLog> audit = auditLogRepository
                    .findByEntityAndEntityIdOrderByIdAsc(AuditEntities.APPOINTMENT, appointmentId);
            assertThat(audit).extracting(AuditLog::getAction).containsExactly(
                    AuditAction.APPOINTMENT_BOOKED.name(), AuditAction.APPOINTMENT_RESCHEDULED.name());
            assertThat(audit).allSatisfy(entry ->
                    assertThat(entry.getUser().getUsername()).isEqualTo(DemoDataLoader.FRONT_DESK_USERNAME));
        }

        @Test
        @DisplayName("Anular desde ventanilla exige el motivo: 400 sin motivo y 200 con motivo auditado")
        void anularExigeElMotivo() throws Exception {
            String token = loginAsFrontDesk();
            long patientId = registerPatient();
            Slot slot = nextFreeSlot();
            long appointmentId = readId(frontDeskBook(token, patientId, slot.getId(), UUID.randomUUID())
                    .andExpect(status().isCreated()).andReturn());

            mockMvc.perform(patch("/api/v1/front-desk/appointments/" + appointmentId + "/cancellation")
                            .headers(bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_DATA"))
                    .andExpect(jsonPath("$.fields[0].field").value("reason"))
                    .andExpect(jsonPath("$.fields[0].message").value("Indica el motivo de la anulación."));
            mockMvc.perform(patch("/api/v1/front-desk/appointments/" + appointmentId + "/cancellation")
                            .headers(bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"   \"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields[0].field").value("reason"));
            mockMvc.perform(patch("/api/v1/front-desk/appointments/" + appointmentId + "/cancellation")
                            .headers(bearer(token)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_DATA"));
            assertThat(slotStatusOf(slot)).isEqualTo(SlotStatus.BOOKED);

            mockMvc.perform(patch("/api/v1/front-desk/appointments/" + appointmentId + "/cancellation")
                            .headers(bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"El paciente viajó\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));

            assertThat(slotStatusOf(slot)).isEqualTo(SlotStatus.FREE);
            List<AuditLog> audit = auditLogRepository
                    .findByEntityAndEntityIdOrderByIdAsc(AuditEntities.APPOINTMENT, appointmentId);
            assertThat(audit.getLast().getAction()).isEqualTo(AuditAction.APPOINTMENT_CANCELLED.name());
            assertThat(audit.getLast().getDetail()).contains("Motivo: El paciente viajó");
            assertThat(appointmentHistoryRepository.findByAppointmentIdOrderByIdAsc(appointmentId).getLast()
                    .getDetail()).isEqualTo("El paciente viajó");
        }

        @Test
        @DisplayName("Las citas del paciente se listan con upcoming=false incluyendo las anuladas")
        void listaLasCitasDelPaciente() throws Exception {
            String token = loginAsFrontDesk();
            long patientId = registerPatient();
            Slot slot = nextFreeSlot();
            long appointmentId = readId(frontDeskBook(token, patientId, slot.getId(), UUID.randomUUID())
                    .andExpect(status().isCreated()).andReturn());
            mockMvc.perform(patch("/api/v1/front-desk/appointments/" + appointmentId + "/cancellation")
                            .headers(bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Cambio de planes\"}"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/front-desk/patients/" + patientId + "/appointments").headers(bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
            MvcResult all = mockMvc.perform(get("/api/v1/front-desk/patients/" + patientId + "/appointments")
                            .param("upcoming", "false").headers(bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andReturn();
            List<String> statuses = JsonPath.read(all.getResponse().getContentAsString(StandardCharsets.UTF_8), "$[*].status");
            assertThat(statuses).containsExactly("CANCELLED");
        }
    }

    @Nested
    @DisplayName("Acceso")
    class Acceso {

        @Test
        @DisplayName("Sin token las rutas de admisión responden 401 UNAUTHORIZED")
        void sinTokenResponde401() throws Exception {
            mockMvc.perform(get("/api/v1/front-desk/patients")
                            .param("documentType", "DNI").param("documentNumber", LUCIA_DNI))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("Un médico recibe 403 FORBIDDEN en /front-desk/**")
        void medicoRecibe403() throws Exception {
            String token = loginAs(DOCTOR_USERNAME, DemoDataLoader.DOCTOR_PASSWORD);

            mockMvc.perform(get("/api/v1/front-desk/patients").headers(bearer(token))
                            .param("documentType", "DNI").param("documentNumber", LUCIA_DNI))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            mockMvc.perform(post("/api/v1/front-desk/appointments").headers(bearer(token))
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"patientId\":1,\"slotId\":1,\"visitReason\":\"Control\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("El administrador también puede operar la ventanilla")
        void administradorPuedeOperarLaVentanilla() throws Exception {
            String token = loginAsAdmin();

            mockMvc.perform(get("/api/v1/front-desk/patients").headers(bearer(token))
                            .param("documentType", "DNI").param("documentNumber", LUCIA_DNI))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.documentNumber").value(LUCIA_DNI));
        }
    }
}
