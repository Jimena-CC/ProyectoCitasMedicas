package com.medicitas.api.doctor;

import com.medicitas.api.appointment.Appointment;
import com.medicitas.api.appointment.AppointmentEvent;
import com.medicitas.api.appointment.AppointmentHistory;
import com.medicitas.api.appointment.AppointmentHistoryRepository;
import com.medicitas.api.appointment.AppointmentRepository;
import com.medicitas.api.appointment.AppointmentStatus;
import com.medicitas.api.appointment.BookingChannel;
import com.medicitas.api.audit.AuditAction;
import com.medicitas.api.audit.AuditService;
import com.medicitas.api.catalog.Doctor;
import com.medicitas.api.catalog.Location;
import com.medicitas.api.catalog.Specialty;
import com.medicitas.api.common.BusinessException;
import com.medicitas.api.common.ErrorCodes;
import com.medicitas.api.doctor.DoctorDtos.AgendaItemResponse;
import com.medicitas.api.doctor.DoctorDtos.AgendaResponse;
import com.medicitas.api.doctor.DoctorDtos.AttendanceRequest;
import com.medicitas.api.doctor.DoctorDtos.AttendanceStatus;
import com.medicitas.api.notification.NotificationService;
import com.medicitas.api.patient.DocumentType;
import com.medicitas.api.patient.Patient;
import com.medicitas.api.schedule.Slot;
import com.medicitas.api.security.AppUser;
import com.medicitas.api.security.CurrentUserService;
import com.medicitas.api.security.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas de la agenda del médico: pertenencia de la cita, estado y fecha para registrar asistencia.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DoctorAgendaService")
class DoctorAgendaServiceTest {

    /** Lunes 14 de setiembre de 2026, 10:00 en Lima. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-14T15:00:00Z"), ZoneId.of("America/Lima"));
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 14);

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private AppointmentHistoryRepository appointmentHistoryRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditService auditService;
    @Mock
    private CurrentUserService currentUserService;

    private DoctorAgendaService service;
    private Doctor doctor;
    private Doctor otherDoctor;
    private AppUser doctorUser;
    private Patient patient;

    @BeforeEach
    void preparar() {
        service = new DoctorAgendaService(appointmentRepository, appointmentHistoryRepository, notificationService,
                auditService, currentUserService, CLOCK);
        doctor = doctor(7L, "Carla", "Benavides Ortiz");
        otherDoctor = doctor(8L, "Andrés", "Villanueva Castro");
        doctorUser = new AppUser("dr.benavides", "hash", UserRole.DOCTOR, doctor);
        ReflectionTestUtils.setField(doctorUser, "id", 3L);
        patient = new Patient(DocumentType.DNI, "45871236", "Lucía", "Paredes Quispe", LocalDate.of(1991, 4, 18),
                "F", "987654321", "lucia.paredes@correo.pe", true, CLOCK.instant());
        ReflectionTestUtils.setField(patient, "id", 1L);
    }

    @Nested
    @DisplayName("Agenda del día")
    class AgendaDelDia {

        @Test
        @DisplayName("Sin fecha consulta la de hoy en la zona de la clínica y resume por estado")
        void sinFechaConsultaHoyYResumePorEstado() {
            when(currentUserService.require()).thenReturn(doctorUser);
            Appointment booked = appointment(10L, doctor, TODAY, LocalTime.of(7, 0));
            Appointment attended = appointment(11L, doctor, TODAY, LocalTime.of(7, 20));
            attended.registerAttendance(AppointmentStatus.ATTENDED, CLOCK.instant());
            Appointment cancelled = appointment(12L, doctor, TODAY, LocalTime.of(7, 40));
            cancelled.cancel(CLOCK.instant());
            when(appointmentRepository.findByDoctorAndDate(7L, TODAY)).thenReturn(List.of(booked, attended, cancelled));

            AgendaResponse agenda = service.agendaOfDay(null);

            assertAll(
                    () -> assertThat(agenda.date()).isEqualTo(TODAY),
                    () -> assertThat(agenda.doctor().id()).isEqualTo(7L),
                    () -> assertThat(agenda.doctor().specialty()).isEqualTo("Cardiología"),
                    () -> assertThat(agenda.summary().total()).isEqualTo(3),
                    () -> assertThat(agenda.summary().booked()).isEqualTo(1),
                    () -> assertThat(agenda.summary().attended()).isEqualTo(1),
                    () -> assertThat(agenda.summary().noShow()).isZero(),
                    () -> assertThat(agenda.summary().cancelled()).isEqualTo(1),
                    () -> assertThat(agenda.items()).extracting(AgendaItemResponse::appointmentId)
                            .containsExactly(10L, 11L, 12L),
                    () -> assertThat(agenda.items().get(0).insurance()).isEqualTo("Particular"),
                    () -> assertThat(agenda.items().get(0).attendanceRegisteredAt()).isNull(),
                    () -> assertThat(agenda.items().get(1).attendanceRegisteredAt()).isNotNull(),
                    () -> assertThat(agenda.items().get(0).patient().fullName()).isEqualTo("Lucía Paredes Quispe"));
        }

        @Test
        @DisplayName("Con fecha consulta ese día para el médico autenticado")
        void conFechaConsultaEseDia() {
            when(currentUserService.require()).thenReturn(doctorUser);
            LocalDate tomorrow = TODAY.plusDays(1);
            when(appointmentRepository.findByDoctorAndDate(7L, tomorrow)).thenReturn(List.of());

            AgendaResponse agenda = service.agendaOfDay(tomorrow);

            assertThat(agenda.date()).isEqualTo(tomorrow);
            assertThat(agenda.items()).isEmpty();
            assertThat(agenda.summary().total()).isZero();
        }

        @Test
        @DisplayName("Un usuario DOCTOR sin médico vinculado recibe 403 DOCTOR_NOT_LINKED")
        void usuarioSinMedicoVinculadoRecibe403() {
            AppUser unlinked = new AppUser("dr.sinficha", "hash", UserRole.DOCTOR, null);
            when(currentUserService.require()).thenReturn(unlinked);

            assertThatThrownBy(() -> service.agendaOfDay(null))
                    .isInstanceOfSatisfying(BusinessException.class, ex -> {
                        assertThat(ex.getCode()).isEqualTo(ErrorCodes.DOCTOR_NOT_LINKED);
                        assertThat(ex.getStatus().value()).isEqualTo(403);
                    });
            verify(appointmentRepository, never()).findByDoctorAndDate(anyLong(), any());
        }
    }

    @Nested
    @DisplayName("Registro de asistencia")
    class RegistroDeAsistencia {

        @ParameterizedTest(name = "{0}")
        @EnumSource(AttendanceStatus.class)
        @DisplayName("Una cita reservada de hoy cambia de estado, deja historial y auditoría y cancela notificaciones")
        void citaReservadaDeHoyRegistraSuAsistencia(AttendanceStatus outcome) {
            when(currentUserService.require()).thenReturn(doctorUser);
            Appointment appointment = appointment(10L, doctor, TODAY, LocalTime.of(7, 0));
            when(appointmentRepository.findDetailById(10L)).thenReturn(Optional.of(appointment));

            AgendaItemResponse item = service.registerAttendance(10L, new AttendanceRequest(outcome, "  Llegó tarde "));

            ArgumentCaptor<AppointmentHistory> history = ArgumentCaptor.forClass(AppointmentHistory.class);
            verify(appointmentHistoryRepository).save(history.capture());
            assertAll(
                    () -> assertThat(appointment.getStatus()).isEqualTo(outcome.toAppointmentStatus()),
                    () -> assertThat(appointment.getUpdatedAt()).isEqualTo(CLOCK.instant()),
                    () -> assertThat(item.status()).isEqualTo(outcome.toAppointmentStatus()),
                    () -> assertThat(item.attendanceRegisteredAt()).isNotNull(),
                    () -> assertThat(history.getValue().getEvent()).isEqualTo(AppointmentEvent.ATTENDANCE),
                    () -> assertThat(history.getValue().getPreviousStatus()).isEqualTo(AppointmentStatus.BOOKED),
                    () -> assertThat(history.getValue().getNewStatus()).isEqualTo(outcome.toAppointmentStatus()),
                    () -> assertThat(history.getValue().getUser()).isSameAs(doctorUser),
                    () -> assertThat(history.getValue().getDetail()).isEqualTo("Llegó tarde"));
            verify(auditService).record(eq(doctorUser), eq(AuditAction.ATTENDANCE_REGISTERED), eq(10L), anyString());
            verify(notificationService).cancelPendingForAttendance(appointment);
        }

        @Test
        @DisplayName("Una cita de un día pasado también admite registro de asistencia y guarda la nota vacía como nula")
        void citaDeDiaPasadoAdmiteRegistro() {
            when(currentUserService.require()).thenReturn(doctorUser);
            Appointment appointment = appointment(10L, doctor, TODAY.minusDays(1), LocalTime.of(7, 0));
            when(appointmentRepository.findDetailById(10L)).thenReturn(Optional.of(appointment));

            service.registerAttendance(10L, new AttendanceRequest(AttendanceStatus.NO_SHOW, "   "));

            ArgumentCaptor<AppointmentHistory> history = ArgumentCaptor.forClass(AppointmentHistory.class);
            verify(appointmentHistoryRepository).save(history.capture());
            assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.NO_SHOW);
            assertThat(history.getValue().getDetail()).isNull();
        }

        @Test
        @DisplayName("Una cita futura responde 422 APPOINTMENT_NOT_MODIFIABLE")
        void citaFuturaResponde422() {
            when(currentUserService.require()).thenReturn(doctorUser);
            Appointment appointment = appointment(10L, doctor, TODAY.plusDays(1), LocalTime.of(7, 0));
            when(appointmentRepository.findDetailById(10L)).thenReturn(Optional.of(appointment));

            assertThatThrownBy(() -> service.registerAttendance(10L, new AttendanceRequest(AttendanceStatus.ATTENDED, null)))
                    .isInstanceOfSatisfying(BusinessException.class, ex -> {
                        assertThat(ex.getCode()).isEqualTo(ErrorCodes.APPOINTMENT_NOT_MODIFIABLE);
                        assertThat(ex.getStatus().value()).isEqualTo(422);
                        assertThat(ex.getMessage()).contains("aún no ha ocurrido");
                    });
            assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
            verify(appointmentHistoryRepository, never()).save(any());
            verify(auditService, never()).record(any(), any(AuditAction.class), anyLong(), anyString());
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = AppointmentStatus.class, names = {"CANCELLED", "ATTENDED", "NO_SHOW"})
        @DisplayName("Una cita que no está reservada responde 422 APPOINTMENT_NOT_MODIFIABLE")
        void citaNoReservadaResponde422(AppointmentStatus current) {
            when(currentUserService.require()).thenReturn(doctorUser);
            Appointment appointment = appointment(10L, doctor, TODAY, LocalTime.of(7, 0));
            if (current == AppointmentStatus.CANCELLED) {
                appointment.cancel(CLOCK.instant());
            } else {
                appointment.registerAttendance(current, CLOCK.instant());
            }
            when(appointmentRepository.findDetailById(10L)).thenReturn(Optional.of(appointment));

            assertThatThrownBy(() -> service.registerAttendance(10L, new AttendanceRequest(AttendanceStatus.ATTENDED, null)))
                    .isInstanceOfSatisfying(BusinessException.class, ex -> {
                        assertThat(ex.getCode()).isEqualTo(ErrorCodes.APPOINTMENT_NOT_MODIFIABLE);
                        assertThat(ex.getStatus().value()).isEqualTo(422);
                    });
            assertThat(appointment.getStatus()).isEqualTo(current);
            verify(notificationService, never()).cancelPendingForAttendance(any());
        }

        @Test
        @DisplayName("La cita de otro médico responde 404 APPOINTMENT_NOT_FOUND sin revelar que existe")
        void citaDeOtroMedicoResponde404() {
            when(currentUserService.require()).thenReturn(doctorUser);
            Appointment appointment = appointment(10L, otherDoctor, TODAY, LocalTime.of(7, 0));
            when(appointmentRepository.findDetailById(10L)).thenReturn(Optional.of(appointment));

            assertThatThrownBy(() -> service.registerAttendance(10L, new AttendanceRequest(AttendanceStatus.ATTENDED, null)))
                    .isInstanceOfSatisfying(BusinessException.class, ex -> {
                        assertThat(ex.getCode()).isEqualTo(ErrorCodes.APPOINTMENT_NOT_FOUND);
                        assertThat(ex.getStatus().value()).isEqualTo(404);
                    });
            assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
            verify(appointmentHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Una cita inexistente responde 404 APPOINTMENT_NOT_FOUND")
        void citaInexistenteResponde404() {
            when(currentUserService.require()).thenReturn(doctorUser);
            when(appointmentRepository.findDetailById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.registerAttendance(99L, new AttendanceRequest(AttendanceStatus.NO_SHOW, null)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo(ErrorCodes.APPOINTMENT_NOT_FOUND));
        }

        @Test
        @DisplayName("Un usuario DOCTOR sin médico vinculado no puede registrar asistencia")
        void usuarioSinMedicoVinculadoNoRegistraAsistencia() {
            AppUser unlinked = new AppUser("dr.sinficha", "hash", UserRole.DOCTOR, null);
            when(currentUserService.require()).thenReturn(unlinked);

            assertThatThrownBy(() -> service.registerAttendance(10L, new AttendanceRequest(AttendanceStatus.ATTENDED, null)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo(ErrorCodes.DOCTOR_NOT_LINKED));
            verify(appointmentRepository, never()).findDetailById(anyLong());
        }
    }

    @Nested
    @DisplayName("Entidad Appointment")
    class EntidadAppointment {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = AppointmentStatus.class, names = {"BOOKED", "CANCELLED"})
        @DisplayName("registerAttendance solo acepta ATTENDED o NO_SHOW")
        void registerAttendanceSoloAceptaResultadosDeAsistencia(AppointmentStatus invalid) {
            Appointment appointment = appointment(10L, doctor, TODAY, LocalTime.of(7, 0));

            assertThatThrownBy(() -> appointment.registerAttendance(invalid, CLOCK.instant()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(appointment.hasAttendanceRegistered()).isFalse();
        }
    }

    private Appointment appointment(long id, Doctor owner, LocalDate date, LocalTime start) {
        Location location = new Location("SI", "Sede San Isidro", "Av. Alfredo Salazar 350", "San Isidro", null);
        ReflectionTestUtils.setField(location, "id", 1L);
        Slot slot = new Slot(owner, location, date, start, start.plusMinutes(20), "Consultorio 304");
        ReflectionTestUtils.setField(slot, "id", id * 100);
        Appointment appointment = new Appointment("MCA-" + id, patient, slot, null, "Control", BookingChannel.KIOSK,
                UUID.randomUUID(), CLOCK.instant().minusSeconds(3600));
        ReflectionTestUtils.setField(appointment, "id", id);
        return appointment;
    }

    private static Doctor doctor(long id, String firstNames, String lastNames) {
        Specialty cardiologia = new Specialty("Cardiología", null, (short) 20);
        ReflectionTestUtils.setField(cardiologia, "id", 1L);
        Doctor doctor = new Doctor(String.valueOf(40000 + id), null, firstNames, lastNames, cardiologia);
        ReflectionTestUtils.setField(doctor, "id", id);
        return doctor;
    }
}
