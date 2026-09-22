package com.medicitas.api.appointment;

import com.medicitas.api.appointment.AppointmentDtos.AppointmentRequest;
import com.medicitas.api.appointment.AppointmentDtos.BookingResult;
import com.medicitas.api.catalog.Doctor;
import com.medicitas.api.catalog.Location;
import com.medicitas.api.catalog.Specialty;
import com.medicitas.api.common.BusinessException;
import com.medicitas.api.common.ErrorCodes;
import com.medicitas.api.notification.NotificationService;
import com.medicitas.api.notification.NotificationsToSendEvent;
import com.medicitas.api.patient.DocumentType;
import com.medicitas.api.patient.Patient;
import com.medicitas.api.patient.PatientInsuranceRepository;
import com.medicitas.api.patient.PatientRepository;
import com.medicitas.api.schedule.Slot;
import com.medicitas.api.schedule.SlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    /** Domingo 13 de setiembre de 2026, 10:00 en Lima. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private SlotRepository slotRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private PatientInsuranceRepository patientInsuranceRepository;
    @Mock
    private AppointmentHistoryRepository appointmentHistoryRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AppointmentCodeGenerator appointmentCodeGenerator;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AppointmentService appointmentService;
    private Patient patient;
    private Slot slot;

    @BeforeEach
    void preparar() {
        appointmentService = new AppointmentService(appointmentRepository, slotRepository, patientRepository,
                patientInsuranceRepository, appointmentHistoryRepository, notificationService,
                appointmentCodeGenerator, eventPublisher, CLOCK);
        patient = new Patient(DocumentType.DNI, "45871236", "Lucía", "Paredes Quispe", LocalDate.of(1991, 4, 18),
                "F", "987654321", "lucia.paredes@correo.pe", true, CLOCK.instant());
        ReflectionTestUtils.setField(patient, "id", 1L);
        slot = slot(10L, LocalDate.of(2026, 9, 14), LocalTime.of(7, 15));
    }

    @Test
    void rechazaLaReservaCuandoOtraSolicitudTomoElCupoPrimero() {
        prepareNewBooking();
        when(slotRepository.take(10L)).thenReturn(0);

        assertThatThrownBy(() -> appointmentService.book(request(), UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCodes.SLOT_NOT_AVAILABLE);
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                });
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void rechazaLaReservaCuandoElPacienteYaTieneUnaCitaEnEseHorario() {
        prepareNewBooking();
        when(appointmentRepository.existsOverlap(eq(1L), eq(AppointmentStatus.BOOKED), anyLong(), eq(slot.getDate()),
                eq(slot.getStartTime()), eq(slot.getEndTime()))).thenReturn(true);

        assertThatThrownBy(() -> appointmentService.book(request(), UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCodes.DUPLICATE_APPOINTMENT));
        verify(slotRepository, never()).take(anyLong());
    }

    @Test
    void rechazaReservarUnCupoQueYaEmpezo() {
        slot = slot(11L, LocalDate.of(2026, 9, 13), LocalTime.of(9, 40));
        when(appointmentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(slotRepository.findDetailById(11L)).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> appointmentService.book(
                new AppointmentRequest(1L, 11L, null, "Control"), UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCodes.SLOT_NOT_AVAILABLE));
        verify(slotRepository, never()).take(anyLong());
    }

    @Test
    void reservaExitosaTomaElCupoRegistraHistorialYPublicaLasNotificaciones() {
        prepareNewBooking();
        when(slotRepository.take(10L)).thenReturn(1);
        when(appointmentCodeGenerator.generate()).thenReturn("MCA-7K2Q9");
        when(notificationService.scheduleForBooking(any())).thenReturn(List.of(5L));

        BookingResult result = appointmentService.book(request(), UUID.randomUUID());

        assertThat(result.created()).isTrue();
        assertThat(result.appointment().code()).isEqualTo("MCA-7K2Q9");
        assertThat(result.appointment().doctor()).isEqualTo("Carla Benavides Ortiz");
        assertThat(result.appointment().coverage()).isEqualTo("Particular");
        verify(appointmentRepository).save(any(Appointment.class));
        verify(appointmentHistoryRepository).save(any(AppointmentHistory.class));
        verify(eventPublisher).publishEvent(new NotificationsToSendEvent(List.of(5L)));
    }

    @Test
    void reintentoConLaMismaClaveDevuelveLaCitaExistenteSinTomarOtroCupo() {
        UUID key = UUID.randomUUID();
        Appointment existing = new Appointment("MCA-ABCDE", patient, slot, null, "Control", BookingChannel.KIOSK,
                key, CLOCK.instant());
        ReflectionTestUtils.setField(existing, "id", 99L);
        when(appointmentRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        BookingResult result = appointmentService.book(request(), key);

        assertThat(result.created()).isFalse();
        assertThat(result.appointment().id()).isEqualTo(99L);
        verify(slotRepository, never()).take(anyLong());
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void noPermiteAnularUnaCitaYaAnulada() {
        Appointment cancelled = new Appointment("MCA-ABCDE", patient, slot, null, "Control", BookingChannel.KIOSK,
                UUID.randomUUID(), CLOCK.instant());
        cancelled.cancel(CLOCK.instant());
        when(appointmentRepository.findDetailById(7L)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> appointmentService.cancel(7L, "Ya no la necesito"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCodes.APPOINTMENT_NOT_MODIFIABLE);
                    assertThat(ex.getStatus().value()).isEqualTo(422);
                });
        verify(slotRepository, never()).release(anyLong());
    }

    private void prepareNewBooking() {
        when(appointmentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(slotRepository.findDetailById(10L)).thenReturn(Optional.of(slot));
    }

    private static AppointmentRequest request() {
        return new AppointmentRequest(1L, 10L, null, "Control de presión arterial");
    }

    private static Slot slot(long id, LocalDate date, LocalTime start) {
        Specialty cardiologia = new Specialty("Cardiología", null, (short) 20);
        ReflectionTestUtils.setField(cardiologia, "id", 1L);
        Doctor doctor = new Doctor("045712", null, "Carla", "Benavides Ortiz", cardiologia);
        ReflectionTestUtils.setField(doctor, "id", 7L);
        Location location = new Location("SI", "Sede San Isidro", "Av. Alfredo Salazar 350", "San Isidro", null);
        ReflectionTestUtils.setField(location, "id", 1L);
        Slot newSlot = new Slot(doctor, location, date, start, start.plusMinutes(20), "Consultorio 304");
        ReflectionTestUtils.setField(newSlot, "id", id);
        return newSlot;
    }
}
