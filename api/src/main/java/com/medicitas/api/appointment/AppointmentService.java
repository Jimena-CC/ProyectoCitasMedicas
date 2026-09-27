package com.medicitas.api.appointment;

import com.medicitas.api.appointment.AppointmentDtos.AppointmentRequest;
import com.medicitas.api.appointment.AppointmentDtos.AppointmentResponse;
import com.medicitas.api.appointment.AppointmentDtos.BookingResult;
import com.medicitas.api.appointment.AppointmentDtos.RescheduleRequest;
import com.medicitas.api.common.BusinessException;
import com.medicitas.api.common.ErrorCodes;
import com.medicitas.api.notification.NotificationService;
import com.medicitas.api.notification.NotificationsToSendEvent;
import com.medicitas.api.patient.Patient;
import com.medicitas.api.patient.PatientInsurance;
import com.medicitas.api.patient.PatientInsuranceRepository;
import com.medicitas.api.patient.PatientRepository;
import com.medicitas.api.schedule.Slot;
import com.medicitas.api.schedule.SlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Reglas de agendamiento del kiosko: reserva, reprogramación y anulación (RF-06 a RF-11, RNF-07).
 */
@Service
public class AppointmentService {

    private static final Logger logger = LoggerFactory.getLogger(AppointmentService.class);

    /** Id que ninguna cita tiene: se usa cuando no hay cita que excluir del control de cruces. */
    private static final long NO_EXCLUSION = -1L;

    private final AppointmentRepository appointmentRepository;
    private final SlotRepository slotRepository;
    private final PatientRepository patientRepository;
    private final PatientInsuranceRepository patientInsuranceRepository;
    private final AppointmentHistoryRepository appointmentHistoryRepository;
    private final NotificationService notificationService;
    private final AppointmentCodeGenerator appointmentCodeGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public AppointmentService(AppointmentRepository appointmentRepository, SlotRepository slotRepository,
                              PatientRepository patientRepository, PatientInsuranceRepository patientInsuranceRepository,
                              AppointmentHistoryRepository appointmentHistoryRepository,
                              NotificationService notificationService,
                              AppointmentCodeGenerator appointmentCodeGenerator,
                              ApplicationEventPublisher eventPublisher, Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.slotRepository = slotRepository;
        this.patientRepository = patientRepository;
        this.patientInsuranceRepository = patientInsuranceRepository;
        this.appointmentHistoryRepository = appointmentHistoryRepository;
        this.notificationService = notificationService;
        this.appointmentCodeGenerator = appointmentCodeGenerator;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public BookingResult book(AppointmentRequest request, UUID idempotencyKey) {
        var previousBooking = appointmentRepository.findByIdempotencyKey(idempotencyKey);
        if (previousBooking.isPresent()) {
            logger.info("Reintento con clave {}: se devuelve la cita {}", idempotencyKey, previousBooking.get().getCode());
            return new BookingResult(AppointmentResponse.from(previousBooking.get(), clock.getZone()), false);
        }

        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(AppointmentService::patientNotFound);
        Slot slot = slotRepository.findDetailById(request.slotId())
                .orElseThrow(AppointmentService::slotNotFound);
        PatientInsurance insurance = resolveInsurance(patient, request.patientInsuranceId());

        validateFutureSlot(slot);
        validateNoOverlap(patient.getId(), slot, NO_EXCLUSION);
        takeSlot(slot.getId());

        Instant now = Instant.now(clock);
        Appointment appointment = new Appointment(appointmentCodeGenerator.generate(), patient, slot, insurance,
                request.visitReason().trim(), BookingChannel.KIOSK, idempotencyKey, now);
        appointmentRepository.save(appointment);
        appointmentHistoryRepository.save(AppointmentHistory.record(appointment, AppointmentEvent.BOOKING, null,
                AppointmentStatus.BOOKED, null, slot, null, now));
        publish(notificationService.scheduleForBooking(appointment));

        logger.info("Cita {} reservada: cupo {} para el paciente {}", appointment.getCode(), slot.getId(), patient.getId());
        return new BookingResult(AppointmentResponse.from(appointment, clock.getZone()), true);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> listByPatient(Long patientId, boolean onlyUpcoming) {
        if (!patientRepository.existsById(patientId)) {
            throw patientNotFound();
        }
        if (!onlyUpcoming) {
            return appointmentRepository.findAllByPatient(patientId).stream()
                    .map(appointment -> AppointmentResponse.from(appointment, clock.getZone()))
                    .toList();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return appointmentRepository.findByPatientStatusFrom(patientId, AppointmentStatus.BOOKED, LocalDate.now(clock)).stream()
                .filter(appointment -> appointment.getSlot().getStart().isAfter(now))
                .map(appointment -> AppointmentResponse.from(appointment, clock.getZone()))
                .toList();
    }

    /**
     * Traslada la cita a otro cupo de la misma especialidad y libera el original (RF-09).
     */
    @Transactional
    public AppointmentResponse reschedule(Long appointmentId, RescheduleRequest request) {
        Appointment appointment = appointmentRepository.findDetailById(appointmentId)
                .orElseThrow(AppointmentService::appointmentNotFound);
        validateModifiable(appointment);

        Slot previousSlot = appointment.getSlot();
        if (previousSlot.getId().equals(request.newSlotId())) {
            throw BusinessException.conflict(ErrorCodes.SLOT_NOT_AVAILABLE,
                    "Tu cita ya está reservada en ese horario. Elige un horario diferente.");
        }
        Slot newSlot = slotRepository.findDetailById(request.newSlotId())
                .orElseThrow(AppointmentService::slotNotFound);
        Long previousSpecialty = previousSlot.getDoctor().getSpecialty().getId();
        if (!newSlot.getDoctor().getSpecialty().getId().equals(previousSpecialty)) {
            throw BusinessException.ruleViolated(ErrorCodes.APPOINTMENT_NOT_MODIFIABLE,
                    "Solo puedes reprogramar a un horario de la misma especialidad. Para otra especialidad, reserva una nueva cita.");
        }
        validateFutureSlot(newSlot);
        validateNoOverlap(appointment.getPatient().getId(), newSlot, appointment.getId());

        takeSlot(newSlot.getId());
        slotRepository.release(previousSlot.getId());

        Instant now = Instant.now(clock);
        appointment.reschedule(newSlot, now);
        appointmentHistoryRepository.save(AppointmentHistory.record(appointment, AppointmentEvent.RESCHEDULE,
                AppointmentStatus.BOOKED, AppointmentStatus.BOOKED, previousSlot, newSlot, null, now));
        publish(notificationService.scheduleForReschedule(appointment));

        logger.info("Cita {} reprogramada del cupo {} al cupo {}", appointment.getCode(), previousSlot.getId(), newSlot.getId());
        return AppointmentResponse.from(appointment, clock.getZone());
    }

    /**
     * Anula una cita reservada y libera su cupo (RF-10).
     */
    @Transactional
    public AppointmentResponse cancel(Long appointmentId, String reason) {
        Appointment appointment = appointmentRepository.findDetailById(appointmentId)
                .orElseThrow(AppointmentService::appointmentNotFound);
        validateModifiable(appointment);

        slotRepository.release(appointment.getSlot().getId());
        Instant now = Instant.now(clock);
        appointment.cancel(now);
        String detail = reason == null || reason.isBlank() ? null : reason.trim();
        appointmentHistoryRepository.save(AppointmentHistory.record(appointment, AppointmentEvent.CANCELLATION,
                AppointmentStatus.BOOKED, AppointmentStatus.CANCELLED, appointment.getSlot(), null, detail, now));
        publish(notificationService.scheduleForCancellation(appointment));

        logger.info("Cita {} anulada; cupo {} liberado", appointment.getCode(), appointment.getSlot().getId());
        return AppointmentResponse.from(appointment, clock.getZone());
    }

    private PatientInsurance resolveInsurance(Patient patient, Long patientInsuranceId) {
        if (patientInsuranceId == null) {
            return null;
        }
        PatientInsurance insurance = patientInsuranceRepository.findById(patientInsuranceId)
                .filter(pi -> pi.getPatient().getId().equals(patient.getId()))
                .orElseThrow(() -> BusinessException.ruleViolated(ErrorCodes.COVERAGE_NOT_ACTIVE,
                        "El seguro indicado no está asociado a tu perfil. Actualiza tu seguro o elige atención particular."));
        if (!insurance.isActiveOn(LocalDate.now(clock))) {
            throw BusinessException.ruleViolated(ErrorCodes.COVERAGE_NOT_ACTIVE,
                    "Tu cobertura no está vigente. Puedes continuar como atención particular o actualizar tu seguro.");
        }
        return insurance;
    }

    private void validateFutureSlot(Slot slot) {
        if (!slot.getStart().isAfter(LocalDateTime.now(clock))) {
            throw BusinessException.conflict(ErrorCodes.SLOT_NOT_AVAILABLE,
                    "Ese horario ya no está disponible. Elige otro horario.");
        }
    }

    private void validateNoOverlap(Long patientId, Slot slot, long excludedAppointmentId) {
        if (appointmentRepository.existsOverlap(patientId, AppointmentStatus.BOOKED, excludedAppointmentId,
                slot.getDate(), slot.getStartTime(), slot.getEndTime())) {
            throw BusinessException.conflict(ErrorCodes.DUPLICATE_APPOINTMENT,
                    "Ya tienes una cita reservada en ese horario. Elige otro horario o revisa tus citas.");
        }
    }

    private void validateModifiable(Appointment appointment) {
        boolean upcoming = appointment.getStatus() == AppointmentStatus.BOOKED
                && appointment.getSlot().getStart().isAfter(LocalDateTime.now(clock));
        if (!upcoming) {
            throw BusinessException.ruleViolated(ErrorCodes.APPOINTMENT_NOT_MODIFIABLE,
                    "Esta cita ya no se puede modificar porque fue anulada, ya fue atendida o su horario ya pasó.");
        }
    }

    /**
     * Toma el cupo con una actualización condicional: si otra reserva simultánea se adelantó,
     * no se actualiza ninguna fila y la solicitud se rechaza (RF-07).
     */
    private void takeSlot(Long slotId) {
        int updatedRows;
        try {
            updatedRows = slotRepository.take(slotId);
        } catch (ConcurrencyFailureException ex) {
            logger.warn("Bloqueo concurrente al tomar el cupo {}: {}", slotId, ex.getMessage());
            updatedRows = 0;
        }
        if (updatedRows == 0) {
            throw BusinessException.conflict(ErrorCodes.SLOT_NOT_AVAILABLE,
                    "Ese horario acaba de ser reservado por otra persona. Elige otro horario disponible.");
        }
    }

    private void publish(List<Long> notificationIds) {
        if (!notificationIds.isEmpty()) {
            eventPublisher.publishEvent(new NotificationsToSendEvent(notificationIds));
        }
    }

    private static BusinessException patientNotFound() {
        return BusinessException.notFound(ErrorCodes.PATIENT_NOT_FOUND,
                "No encontramos al paciente. Vuelve a identificarte con tu documento.");
    }

    private static BusinessException appointmentNotFound() {
        return BusinessException.notFound(ErrorCodes.APPOINTMENT_NOT_FOUND,
                "No encontramos la cita. Revisa tus citas vigentes.");
    }

    private static BusinessException slotNotFound() {
        return BusinessException.notFound(ErrorCodes.RESOURCE_NOT_FOUND,
                "El horario seleccionado no existe. Vuelve a consultar la disponibilidad.");
    }
}
