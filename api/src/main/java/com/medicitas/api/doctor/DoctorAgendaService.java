package com.medicitas.api.doctor;

import com.medicitas.api.appointment.Appointment;
import com.medicitas.api.appointment.AppointmentEvent;
import com.medicitas.api.appointment.AppointmentHistory;
import com.medicitas.api.appointment.AppointmentHistoryRepository;
import com.medicitas.api.appointment.AppointmentRepository;
import com.medicitas.api.appointment.AppointmentStatus;
import com.medicitas.api.audit.AuditAction;
import com.medicitas.api.audit.AuditFormats;
import com.medicitas.api.audit.AuditService;
import com.medicitas.api.catalog.Doctor;
import com.medicitas.api.common.BusinessException;
import com.medicitas.api.common.ErrorCodes;
import com.medicitas.api.doctor.DoctorDtos.AgendaItemResponse;
import com.medicitas.api.doctor.DoctorDtos.AgendaResponse;
import com.medicitas.api.doctor.DoctorDtos.AgendaSummary;
import com.medicitas.api.doctor.DoctorDtos.AttendanceRequest;
import com.medicitas.api.doctor.DoctorDtos.DoctorSummary;
import com.medicitas.api.notification.NotificationService;
import com.medicitas.api.security.AppUser;
import com.medicitas.api.security.CurrentUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Agenda del día y registro de asistencia del médico (RF-20, RF-21; CU-011, CU-012).
 * El médico se resuelve desde el usuario autenticado; las operaciones públicas reciben además una
 * variante parametrizada por {@link Doctor} para que, más adelante, administración pueda consultar
 * la agenda de cualquier médico sin duplicar reglas.
 */
@Service
public class DoctorAgendaService {

    private static final Logger logger = LoggerFactory.getLogger(DoctorAgendaService.class);

    private final AppointmentRepository appointmentRepository;
    private final AppointmentHistoryRepository appointmentHistoryRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public DoctorAgendaService(AppointmentRepository appointmentRepository,
                               AppointmentHistoryRepository appointmentHistoryRepository,
                               NotificationService notificationService, AuditService auditService,
                               CurrentUserService currentUserService, Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentHistoryRepository = appointmentHistoryRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    /**
     * Agenda del médico autenticado en la fecha indicada.
     *
     * @param date fecha consultada, o {@code null} para hoy en la zona horaria de la clínica
     * @return la agenda con su resumen y sus citas ordenadas por hora
     * @throws BusinessException {@code 403 DOCTOR_NOT_LINKED} si el usuario no tiene un médico vinculado
     */
    @Transactional(readOnly = true)
    public AgendaResponse agendaOfDay(LocalDate date) {
        return agendaOfDay(currentDoctor(), date);
    }

    /**
     * Agenda de un médico en la fecha indicada.
     *
     * @param doctor médico dueño de la agenda
     * @param date   fecha consultada, o {@code null} para hoy en la zona horaria de la clínica
     * @return la agenda con su resumen y sus citas ordenadas por hora
     */
    @Transactional(readOnly = true)
    public AgendaResponse agendaOfDay(Doctor doctor, LocalDate date) {
        LocalDate day = date == null ? LocalDate.now(clock) : date;
        List<Appointment> appointments = appointmentRepository.findByDoctorAndDate(doctor.getId(), day);
        List<AgendaItemResponse> items = appointments.stream()
                .map(appointment -> AgendaItemResponse.from(appointment, clock.getZone()))
                .toList();
        return new AgendaResponse(day, DoctorSummary.from(doctor), AgendaSummary.of(appointments), items);
    }

    /**
     * Registra la asistencia o inasistencia de una cita del médico autenticado.
     *
     * @param appointmentId cita
     * @param request       resultado y nota opcional
     * @return el item de agenda actualizado
     * @throws BusinessException {@code 403 DOCTOR_NOT_LINKED} si el usuario no tiene un médico vinculado;
     *                           ver {@link #registerAttendance(Doctor, AppUser, Long, AttendanceRequest)} para el resto
     */
    @Transactional
    public AgendaItemResponse registerAttendance(Long appointmentId, AttendanceRequest request) {
        AppUser user = currentUserService.require();
        return registerAttendance(linkedDoctor(user), user, appointmentId, request);
    }

    /**
     * Registra la asistencia o inasistencia de una cita de un médico. La cita cambia a {@code ATTENDED} o
     * {@code NO_SHOW}, se guarda el evento {@code ATTENDANCE} en su historial, se audita como
     * {@code ATTENDANCE_REGISTERED} y se cancelan sus notificaciones pendientes.
     *
     * @param doctor        médico dueño de la cita
     * @param user          usuario que registra la asistencia; queda en el historial y la auditoría
     * @param appointmentId cita
     * @param request       resultado y nota opcional
     * @return el item de agenda actualizado
     * @throws BusinessException {@code 404 APPOINTMENT_NOT_FOUND} si la cita no existe o es de otro médico;
     *                           {@code 422 APPOINTMENT_NOT_MODIFIABLE} si no está reservada o su fecha aún no llega
     */
    @Transactional
    public AgendaItemResponse registerAttendance(Doctor doctor, AppUser user, Long appointmentId,
                                                 AttendanceRequest request) {
        Appointment appointment = appointmentRepository.findDetailById(appointmentId)
                .filter(found -> found.getSlot().getDoctor().getId().equals(doctor.getId()))
                .orElseThrow(DoctorAgendaService::appointmentNotFound);
        validateAttendanceAllowed(appointment);

        AppointmentStatus outcome = request.status().toAppointmentStatus();
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        Instant now = Instant.now(clock);
        appointment.registerAttendance(outcome, now);
        appointmentHistoryRepository.save(AppointmentHistory.record(appointment, AppointmentEvent.ATTENDANCE,
                AppointmentStatus.BOOKED, outcome, null, appointment.getSlot(), user, note, now));
        auditService.record(user, AuditAction.ATTENDANCE_REGISTERED, appointment.getId(),
                "Cita " + appointment.getCode() + " de " + AuditFormats.patient(appointment.getPatient()) + ": "
                        + describe(outcome) + " (" + AuditFormats.slot(appointment.getSlot()) + ")" + (note == null ? "" : ". Nota: " + note));
        notificationService.cancelPendingForAttendance(appointment);

        logger.info("Cita {}: asistencia registrada como {} por el usuario {}", appointment.getCode(), outcome,
                user.getUsername());
        return AgendaItemResponse.from(appointment, clock.getZone());
    }

    /**
     * Médico vinculado al usuario autenticado.
     *
     * @return el médico
     * @throws BusinessException {@code 403 DOCTOR_NOT_LINKED} si el usuario no tiene un médico vinculado
     */
    @Transactional(readOnly = true)
    public Doctor currentDoctor() {
        return linkedDoctor(currentUserService.require());
    }

    private static Doctor linkedDoctor(AppUser user) {
        Doctor doctor = user.getDoctor();
        if (doctor == null) {
            throw BusinessException.forbidden(ErrorCodes.DOCTOR_NOT_LINKED,
                    "Tu usuario no está vinculado a un médico. Comunícate con el administrador del sistema.");
        }
        return doctor;
    }

    /**
     * Solo se registra asistencia de citas reservadas cuya fecha ya llegó (hoy o antes, en la zona de la clínica).
     */
    private void validateAttendanceAllowed(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw BusinessException.ruleViolated(ErrorCodes.APPOINTMENT_NOT_MODIFIABLE,
                    "Solo puedes registrar la asistencia de una cita reservada. Esta cita ya fue anulada o ya tiene su asistencia registrada.");
        }
        if (appointment.getSlot().getDate().isAfter(LocalDate.now(clock))) {
            throw BusinessException.ruleViolated(ErrorCodes.APPOINTMENT_NOT_MODIFIABLE,
                    "Esta cita aún no ha ocurrido. Registra la asistencia el día de la atención.");
        }
    }

    /** Resultado en forma legible para la auditoría, sin nombres técnicos (RNF-06). */
    private static String describe(AppointmentStatus outcome) {
        return outcome == AppointmentStatus.ATTENDED ? "paciente atendido" : "paciente no se presentó";
    }

    private static BusinessException appointmentNotFound() {
        return BusinessException.notFound(ErrorCodes.APPOINTMENT_NOT_FOUND,
                "No encontramos la cita en tu agenda. Actualiza la agenda del día.");
    }
}
