package com.medicitas.api.doctor;

import com.jayway.jsonpath.JsonPath;
import com.medicitas.api.ApiIntegrationTestSupport;
import com.medicitas.api.appointment.Appointment;
import com.medicitas.api.appointment.AppointmentCodeGenerator;
import com.medicitas.api.appointment.AppointmentEvent;
import com.medicitas.api.appointment.AppointmentHistory;
import com.medicitas.api.appointment.AppointmentHistoryRepository;
import com.medicitas.api.appointment.AppointmentRepository;
import com.medicitas.api.appointment.AppointmentStatus;
import com.medicitas.api.appointment.BookingChannel;
import com.medicitas.api.audit.AuditAction;
import com.medicitas.api.audit.AuditAction.AuditEntities;
import com.medicitas.api.audit.AuditLog;
import com.medicitas.api.audit.AuditLogRepository;
import com.medicitas.api.catalog.Doctor;
import com.medicitas.api.catalog.Location;
import com.medicitas.api.catalog.LocationRepository;
import com.medicitas.api.demo.DemoDataLoader;
import com.medicitas.api.notification.Notification;
import com.medicitas.api.notification.NotificationChannel;
import com.medicitas.api.notification.NotificationRepository;
import com.medicitas.api.notification.NotificationStatus;
import com.medicitas.api.notification.NotificationType;
import com.medicitas.api.patient.Patient;
import com.medicitas.api.patient.PatientRepository;
import com.medicitas.api.schedule.Slot;
import com.medicitas.api.schedule.SlotStatus;
import com.medicitas.api.security.AppUser;
import com.medicitas.api.security.AppUserRepository;
import com.medicitas.api.security.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Agenda del día y registro de asistencia por la API. Los datos de demostración solo tienen cupos de 07:00 a
 * 12:40, y una reserva por la API exige un cupo futuro; por eso las citas de hoy y de días pasados se crean
 * directamente con los repositorios en horarios que no chocan con la demo.
 */
@DisplayName("API del médico")
class DoctorAgendaApiTest extends ApiIntegrationTestSupport {

    private static final String DOCTOR_USERNAME = DemoDataLoader.DOCTOR_USERNAME_PREFIX + "benavides";
    private static final String OTHER_DOCTOR_USERNAME = DemoDataLoader.DOCTOR_USERNAME_PREFIX + "villanueva";

    /** Cada cita de prueba usa un horario distinto desde las 14:00, fuera de la agenda de demostración. */
    private static final AtomicInteger NEXT_SLOT = new AtomicInteger();
    private static final LocalTime FIRST_TEST_HOUR = LocalTime.of(14, 0);

    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private AppointmentHistoryRepository appointmentHistoryRepository;
    @Autowired
    private AppointmentCodeGenerator appointmentCodeGenerator;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private AppUser doctorUser;
    private AppUser otherDoctorUser;
    private LocalDate today;

    @BeforeEach
    void preparar() {
        doctorUser = appUserRepository.findByUsernameIgnoreCase(DOCTOR_USERNAME).orElseThrow();
        otherDoctorUser = appUserRepository.findByUsernameIgnoreCase(OTHER_DOCTOR_USERNAME).orElseThrow();
        today = LocalDate.now(clock);
    }

    private String loginAsDoctor() throws Exception {
        return loginAs(DOCTOR_USERNAME, DemoDataLoader.DOCTOR_PASSWORD);
    }

    /**
     * Crea un cupo ocupado y su cita reservada para el médico en la fecha indicada, con su traza de reserva.
     */
    private Appointment createAppointment(Doctor doctor, LocalDate date) throws Exception {
        Patient patient = patientRepository.findById(registerPatient()).orElseThrow();
        Location location = locationRepository.findByActiveTrueOrderByIdAsc().getFirst();
        LocalTime start = FIRST_TEST_HOUR.plusMinutes(5L * NEXT_SLOT.getAndIncrement());
        Slot slot = new Slot(doctor, location, date, start, start.plusMinutes(5), "Consultorio 999");
        slot.setStatus(SlotStatus.BOOKED);
        slotRepository.save(slot);
        Instant now = Instant.now(clock);
        Appointment appointment = appointmentRepository.save(new Appointment(appointmentCodeGenerator.generate(),
                patient, slot, null, "Control de prueba", BookingChannel.FRONT_DESK, UUID.randomUUID(), now));
        appointmentHistoryRepository.save(AppointmentHistory.record(appointment, AppointmentEvent.BOOKING, null,
                AppointmentStatus.BOOKED, null, slot, null, null, now));
        return appointment;
    }

    private String attendanceBody(String status, String note) {
        return note == null
                ? """
                {"status":"%s"}""".formatted(status)
                : """
                {"status":"%s","note":"%s"}""".formatted(status, note);
    }

    @Nested
    @DisplayName("Agenda del día")
    class AgendaDelDia {

        @Test
        @DisplayName("Sin fecha devuelve la agenda de hoy del médico autenticado, con resumen e items")
        void sinFechaDevuelveLaAgendaDeHoy() throws Exception {
            Appointment mine = createAppointment(doctorUser.getDoctor(), today);
            String token = loginAsDoctor();

            mockMvc.perform(get("/api/v1/doctor/agenda").headers(bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.date").value(today.toString()))
                    .andExpect(jsonPath("$.doctor.id").value(doctorUser.getDoctorId()))
                    .andExpect(jsonPath("$.doctor.fullName").value("Carla Benavides Ortiz"))
                    .andExpect(jsonPath("$.doctor.specialty").value("Cardiología"))
                    .andExpect(jsonPath("$.summary.total").isNumber())
                    .andExpect(jsonPath("$.summary.booked").isNumber())
                    .andExpect(jsonPath("$.items[*].appointmentId", hasItem(mine.getId().intValue())))
                    .andExpect(jsonPath("$.items[?(@.appointmentId == %d)].status".formatted(mine.getId()),
                            hasItem("BOOKED")))
                    .andExpect(jsonPath("$.items[?(@.appointmentId == %d)].insurance".formatted(mine.getId()),
                            hasItem("Particular")))
                    .andExpect(jsonPath("$.items[?(@.appointmentId == %d)].bookingChannel".formatted(mine.getId()),
                            hasItem("FRONT_DESK")))
                    .andExpect(jsonPath("$.items[?(@.appointmentId == %d)].patient.documentType".formatted(mine.getId()),
                            hasItem("DNI")))
                    .andExpect(jsonPath("$.items[?(@.appointmentId == %d)].room".formatted(mine.getId()),
                            hasItem("Consultorio 999")));
        }

        @Test
        @DisplayName("El médico solo ve sus citas: las de otro médico en la misma fecha no aparecen")
        void elMedicoSoloVeSusCitas() throws Exception {
            LocalDate day = today.minusDays(3);
            Appointment mine = createAppointment(doctorUser.getDoctor(), day);
            Appointment foreign = createAppointment(otherDoctorUser.getDoctor(), day);
            String token = loginAsDoctor();

            mockMvc.perform(get("/api/v1/doctor/agenda").param("date", day.toString()).headers(bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.date").value(day.toString()))
                    .andExpect(jsonPath("$.items[*].appointmentId", hasItem(mine.getId().intValue())))
                    .andExpect(jsonPath("$.items[*].appointmentId", not(hasItem(foreign.getId().intValue()))));

            String otherToken = loginAs(OTHER_DOCTOR_USERNAME, DemoDataLoader.DOCTOR_PASSWORD);
            mockMvc.perform(get("/api/v1/doctor/agenda").param("date", day.toString()).headers(bearer(otherToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.doctor.id").value(otherDoctorUser.getDoctorId()))
                    .andExpect(jsonPath("$.items[*].appointmentId", hasItem(foreign.getId().intValue())))
                    .andExpect(jsonPath("$.items[*].appointmentId", not(hasItem(mine.getId().intValue()))));
        }

        @Test
        @DisplayName("Los items vienen ordenados por hora y el resumen cuadra con ellos")
        void losItemsVienenOrdenadosPorHora() throws Exception {
            LocalDate day = today.minusDays(4);
            createAppointment(doctorUser.getDoctor(), day);
            createAppointment(doctorUser.getDoctor(), day);
            String token = loginAsDoctor();

            MvcResult result = mockMvc.perform(get("/api/v1/doctor/agenda").param("date", day.toString())
                            .headers(bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(2))
                    .andExpect(jsonPath("$.summary.total").value(2))
                    .andExpect(jsonPath("$.summary.booked").value(2))
                    .andExpect(jsonPath("$.summary.attended").value(0))
                    .andExpect(jsonPath("$.items[0].startTime", matchesPattern("\\d{2}:\\d{2}")))
                    .andReturn();

            List<String> startTimes = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8),
                    "$.items[*].startTime");
            assertThat(startTimes).isSorted();
        }

        @Test
        @DisplayName("Una fecha con formato inválido responde 400 INVALID_DATA")
        void fechaInvalidaResponde400() throws Exception {
            String token = loginAsDoctor();

            mockMvc.perform(get("/api/v1/doctor/agenda").param("date", "20-10-2026").headers(bearer(token)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_DATA"))
                    .andExpect(jsonPath("$.fields[0].field").value("date"));
        }

        @Test
        @DisplayName("Un usuario DOCTOR sin médico vinculado recibe 403 DOCTOR_NOT_LINKED")
        void usuarioSinMedicoVinculadoRecibe403() throws Exception {
            String username = "dr.sinficha." + UUID.randomUUID().toString().substring(0, 8);
            appUserRepository.save(new AppUser(username, passwordEncoder.encode("clave123"), UserRole.DOCTOR, null));
            String token = loginAs(username, "clave123");

            mockMvc.perform(get("/api/v1/doctor/agenda").headers(bearer(token)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("DOCTOR_NOT_LINKED"))
                    .andExpect(jsonPath("$.message").value(
                            "Tu usuario no está vinculado a un médico. Comunícate con el administrador del sistema."));
        }
    }

    @Nested
    @DisplayName("Registro de asistencia")
    class RegistroDeAsistencia {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"ATTENDED", "NO_SHOW"})
        @DisplayName("Marcar una cita de hoy actualiza su estado, el historial con el médico, la auditoría y cancela notificaciones pendientes")
        void marcarUnaCitaDeHoyActualizaEstadoHistorialYAuditoria(String outcome) throws Exception {
            Appointment appointment = createAppointment(doctorUser.getDoctor(), today);
            notificationRepository.save(Notification.create(appointment, NotificationType.REMINDER,
                    NotificationChannel.SMS, "912345678", Instant.now(clock).plus(Duration.ofHours(1))));
            String token = loginAsDoctor();

            mockMvc.perform(patch("/api/v1/doctor/appointments/" + appointment.getId() + "/attendance")
                            .headers(bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(attendanceBody(outcome, "Paciente de control")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.appointmentId").value(appointment.getId()))
                    .andExpect(jsonPath("$.code").value(appointment.getCode()))
                    .andExpect(jsonPath("$.status").value(outcome))
                    .andExpect(jsonPath("$.attendanceRegisteredAt", matchesPattern(".+-05:00")));

            Appointment updated = appointmentRepository.findById(appointment.getId()).orElseThrow();
            List<AppointmentHistory> history = appointmentHistoryRepository
                    .findByAppointmentIdOrderByIdAsc(appointment.getId());
            List<AuditLog> audit = auditLogRepository
                    .findByEntityAndEntityIdOrderByIdAsc(AuditEntities.APPOINTMENT, appointment.getId());
            List<Notification> notifications = notificationRepository
                    .findByAppointmentIdOrderByScheduledForAscIdAsc(appointment.getId());
            assertAll(
                    () -> assertThat(updated.getStatus()).isEqualTo(AppointmentStatus.valueOf(outcome)),
                    () -> assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(updated.getBookedAt()),
                    () -> assertThat(history).hasSize(2),
                    () -> assertThat(history.getLast().getEvent()).isEqualTo(AppointmentEvent.ATTENDANCE),
                    () -> assertThat(history.getLast().getPreviousStatus()).isEqualTo(AppointmentStatus.BOOKED),
                    () -> assertThat(history.getLast().getNewStatus()).isEqualTo(AppointmentStatus.valueOf(outcome)),
                    () -> assertThat(history.getLast().getDetail()).isEqualTo("Paciente de control"),
                    () -> assertThat(history.getLast().getUser()).isNotNull(),
                    () -> assertThat(history.getLast().getUser().getId()).isEqualTo(doctorUser.getId()),
                    () -> assertThat(audit).hasSize(1),
                    () -> assertThat(audit.getFirst().getAction()).isEqualTo(AuditAction.ATTENDANCE_REGISTERED.name()),
                    () -> assertThat(audit.getFirst().getUser().getUsername()).isEqualTo(DOCTOR_USERNAME),
                    () -> assertThat(audit.getFirst().getDetail()).contains(appointment.getCode())
                            .contains("Nota: Paciente de control"),
                    () -> assertThat(notifications).extracting(Notification::getStatus)
                            .containsOnly(NotificationStatus.CANCELLED));
        }

        @Test
        @DisplayName("Una cita de un día pasado también admite registrar la asistencia, sin nota")
        void unaCitaDeUnDiaPasadoAdmiteRegistro() throws Exception {
            Appointment appointment = createAppointment(doctorUser.getDoctor(), today.minusDays(1));
            String token = loginAsDoctor();

            mockMvc.perform(patch("/api/v1/doctor/appointments/" + appointment.getId() + "/attendance")
                            .headers(bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(attendanceBody("NO_SHOW", null)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("NO_SHOW"));

            assertThat(appointmentHistoryRepository.findByAppointmentIdOrderByIdAsc(appointment.getId()).getLast()
                    .getDetail()).isNull();
        }

        @Test
        @DisplayName("No se puede marcar asistencia de una cita futura: 422 APPOINTMENT_NOT_MODIFIABLE")
        void noSePuedeMarcarUnaCitaFutura() throws Exception {
            Appointment appointment = createAppointment(doctorUser.getDoctor(), today.plusDays(2));
            String token = loginAsDoctor();

            mockMvc.perform(patch("/api/v1/doctor/appointments/" + appointment.getId() + "/attendance")
                            .headers(bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(attendanceBody("ATTENDED", null)))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_MODIFIABLE"))
                    .andExpect(jsonPath("$.message").value(
                            "Esta cita aún no ha ocurrido. Registra la asistencia el día de la atención."));

            assertThat(appointmentRepository.findById(appointment.getId()).orElseThrow().getStatus())
                    .isEqualTo(AppointmentStatus.BOOKED);
        }

        @Test
        @DisplayName("Una cita anulada o ya marcada responde 422 APPOINTMENT_NOT_MODIFIABLE")
        void unaCitaAnuladaOYaMarcadaResponde422() throws Exception {
            Appointment cancelled = createAppointment(doctorUser.getDoctor(), today);
            cancelled.cancel(Instant.now(clock));
            appointmentRepository.save(cancelled);
            Appointment attended = createAppointment(doctorUser.getDoctor(), today);
            String token = loginAsDoctor();
            mockMvc.perform(patch("/api/v1/doctor/appointments/" + attended.getId() + "/attendance")
                            .headers(bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(attendanceBody("ATTENDED", null)))
                    .andExpect(status().isOk());

            for (Appointment appointment : List.of(cancelled, attended)) {
                mockMvc.perform(patch("/api/v1/doctor/appointments/" + appointment.getId() + "/attendance")
                                .headers(bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(attendanceBody("NO_SHOW", null)))
                        .andExpect(status().isUnprocessableContent())
                        .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_MODIFIABLE"));
            }
            assertThat(appointmentRepository.findById(cancelled.getId()).orElseThrow().getStatus())
                    .isEqualTo(AppointmentStatus.CANCELLED);
            assertThat(appointmentRepository.findById(attended.getId()).orElseThrow().getStatus())
                    .isEqualTo(AppointmentStatus.ATTENDED);
        }

        @Test
        @DisplayName("La cita de otro médico responde 404 APPOINTMENT_NOT_FOUND y no cambia")
        void laCitaDeOtroMedicoResponde404() throws Exception {
            Appointment foreign = createAppointment(otherDoctorUser.getDoctor(), today);
            String token = loginAsDoctor();

            mockMvc.perform(patch("/api/v1/doctor/appointments/" + foreign.getId() + "/attendance")
                            .headers(bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(attendanceBody("ATTENDED", null)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_FOUND"));

            assertThat(appointmentRepository.findById(foreign.getId()).orElseThrow().getStatus())
                    .isEqualTo(AppointmentStatus.BOOKED);
            assertThat(appointmentHistoryRepository.findByAppointmentIdOrderByIdAsc(foreign.getId())).hasSize(1);
        }

        @Test
        @DisplayName("Un estado distinto de ATTENDED o NO_SHOW, o sin estado, responde 400 INVALID_DATA")
        void unEstadoInvalidoResponde400() throws Exception {
            Appointment appointment = createAppointment(doctorUser.getDoctor(), today);
            String token = loginAsDoctor();

            mockMvc.perform(patch("/api/v1/doctor/appointments/" + appointment.getId() + "/attendance")
                            .headers(bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(attendanceBody("CANCELLED", null)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_DATA"));
            mockMvc.perform(patch("/api/v1/doctor/appointments/" + appointment.getId() + "/attendance")
                            .headers(bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"note\":\"Sin estado\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_DATA"))
                    .andExpect(jsonPath("$.fields[0].field").value("status"));
            mockMvc.perform(patch("/api/v1/doctor/appointments/" + appointment.getId() + "/attendance")
                            .headers(bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(attendanceBody("ATTENDED", "x".repeat(251))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields[0].field").value("note"));

            assertThat(appointmentRepository.findById(appointment.getId()).orElseThrow().getStatus())
                    .isEqualTo(AppointmentStatus.BOOKED);
        }
    }

    @Nested
    @DisplayName("Acceso")
    class Acceso {

        @Test
        @DisplayName("Sin token las rutas del médico responden 401 UNAUTHORIZED")
        void sinTokenResponde401() throws Exception {
            mockMvc.perform(get("/api/v1/doctor/agenda"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
            mockMvc.perform(patch("/api/v1/doctor/appointments/1/attendance")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(attendanceBody("ATTENDED", null)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Admisión y administración reciben 403 FORBIDDEN en /doctor/**")
        void admisionYAdministracionReciben403() throws Exception {
            for (String token : List.of(loginAsFrontDesk(), loginAsAdmin())) {
                mockMvc.perform(get("/api/v1/doctor/agenda").headers(bearer(token)))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
                mockMvc.perform(patch("/api/v1/doctor/appointments/1/attendance")
                                .headers(bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(attendanceBody("ATTENDED", null)))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            }
        }
    }
}
