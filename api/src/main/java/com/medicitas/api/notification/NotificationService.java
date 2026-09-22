package com.medicitas.api.notification;

import com.medicitas.api.appointment.Appointment;
import com.medicitas.api.appointment.AppointmentRepository;
import com.medicitas.api.appointment.AppointmentStatus;
import com.medicitas.api.common.BusinessException;
import com.medicitas.api.common.ErrorCodes;
import com.medicitas.api.patient.Patient;
import com.medicitas.api.schedule.Slot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Programa y envía las confirmaciones y recordatorios de las citas (RF-11 a RF-14, proceso 07).
 */
@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private static final Locale ES_PE = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter MESSAGE_DATE = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES_PE);
    private static final DateTimeFormatter MESSAGE_TIME = DateTimeFormatter.ofPattern("HH:mm", ES_PE);

    private final NotificationRepository notificationRepository;
    private final AppointmentRepository appointmentRepository;
    private final MessagingClient messagingClient;
    private final MessagingProperties properties;
    private final Clock clock;

    public NotificationService(NotificationRepository notificationRepository, AppointmentRepository appointmentRepository,
                               MessagingClient messagingClient, MessagingProperties properties, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.appointmentRepository = appointmentRepository;
        this.messagingClient = messagingClient;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Confirmación inmediata por correo y recordatorio por SMS antes de la cita.
     *
     * @return ids de las notificaciones que deben enviarse ya
     */
    @Transactional
    public List<Long> scheduleForBooking(Appointment appointment) {
        if (!appointment.getPatient().isAcceptsNotifications()) {
            return List.of();
        }
        Instant now = Instant.now(clock);
        List<Notification> created = new ArrayList<>();
        created.add(Notification.create(appointment, NotificationType.CONFIRMATION, NotificationChannel.EMAIL,
                appointment.getPatient().getEmail(), now));
        created.add(reminder(appointment, now));
        return saveAndCollectImmediate(created, now);
    }

    @Transactional
    public List<Long> scheduleForReschedule(Appointment appointment) {
        cancelPendingReminders(appointment);
        if (!appointment.getPatient().isAcceptsNotifications()) {
            return List.of();
        }
        Instant now = Instant.now(clock);
        List<Notification> created = new ArrayList<>();
        created.add(Notification.create(appointment, NotificationType.RESCHEDULE, NotificationChannel.EMAIL,
                appointment.getPatient().getEmail(), now));
        created.add(reminder(appointment, now));
        return saveAndCollectImmediate(created, now);
    }

    @Transactional
    public List<Long> scheduleForCancellation(Appointment appointment) {
        cancelPendingReminders(appointment);
        if (!appointment.getPatient().isAcceptsNotifications()) {
            return List.of();
        }
        Instant now = Instant.now(clock);
        return saveAndCollectImmediate(List.of(Notification.create(appointment, NotificationType.CANCELLATION,
                NotificationChannel.EMAIL, appointment.getPatient().getEmail(), now)), now);
    }

    /**
     * Intenta entregar una notificación pendiente. Un fallo del proveedor solo afecta a la notificación,
     * nunca al estado de la cita (RF-14).
     */
    @Transactional
    public void send(Long notificationId) {
        Notification notification = notificationRepository.findDetailById(notificationId).orElse(null);
        if (notification == null || notification.getStatus() != NotificationStatus.PENDING) {
            return;
        }
        Appointment appointment = notification.getAppointment();
        if (notification.getType() != NotificationType.CANCELLATION
                && appointment.getStatus() != AppointmentStatus.BOOKED) {
            notification.cancel();
            logger.info("Notificación {} cancelada: la cita {} ya no está reservada", notificationId, appointment.getCode());
            return;
        }

        Instant now = Instant.now(clock);
        try {
            String messageId = messagingClient.send(composeMessage(notification));
            notification.markSent(now, messageId);
        } catch (MessagingException | RuntimeException ex) {
            notification.recordFailure(now, ex.getMessage(), properties.maxAttempts(),
                    Duration.ofMinutes(properties.retryMinutes()));
            logger.warn("No se pudo enviar la notificación {} (intento {} de {}): {}", notificationId,
                    notification.getAttempts(), properties.maxAttempts(), ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<Long> duePendingIds() {
        return notificationRepository.findIdsByStatusScheduledUntil(NotificationStatus.PENDING, Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listByAppointment(Long appointmentId) {
        if (!appointmentRepository.existsById(appointmentId)) {
            throw BusinessException.notFound(ErrorCodes.APPOINTMENT_NOT_FOUND,
                    "No encontramos la cita. Revisa tus citas vigentes.");
        }
        ZoneId zone = clock.getZone();
        return notificationRepository.findByAppointmentIdOrderByScheduledForAscIdAsc(appointmentId).stream()
                .map(n -> new NotificationResponse(n.getType(), n.getChannel(), n.getStatus(),
                        n.getScheduledFor().atZone(zone).toOffsetDateTime(),
                        n.getSentAt() == null ? null : n.getSentAt().atZone(zone).toOffsetDateTime(),
                        n.getAttempts()))
                .toList();
    }

    private Notification reminder(Appointment appointment, Instant now) {
        Instant scheduled = appointment.getSlot().startAt(clock.getZone())
                .minus(Duration.ofHours(properties.reminderHoursAhead()));
        if (scheduled.isBefore(now)) {
            scheduled = now;
        }
        return Notification.create(appointment, NotificationType.REMINDER, NotificationChannel.SMS,
                appointment.getPatient().getPhone(), scheduled);
    }

    private void cancelPendingReminders(Appointment appointment) {
        int cancelled = notificationRepository.cancelPending(appointment.getId(), NotificationType.REMINDER,
                NotificationStatus.PENDING, NotificationStatus.CANCELLED);
        if (cancelled > 0) {
            logger.info("{} recordatorio(s) pendiente(s) cancelado(s) para la cita {}", cancelled, appointment.getCode());
        }
    }

    private List<Long> saveAndCollectImmediate(List<Notification> created, Instant now) {
        notificationRepository.saveAll(created);
        return created.stream()
                .filter(notification -> !notification.getScheduledFor().isAfter(now))
                .map(Notification::getId)
                .toList();
    }

    private OutgoingMessage composeMessage(Notification notification) {
        Appointment appointment = notification.getAppointment();
        Slot slot = appointment.getSlot();
        Patient patient = appointment.getPatient();
        String appointmentDetail = "%s con %s el %s a las %s en %s, %s".formatted(
                slot.getDoctor().getSpecialty().getName(), slot.getDoctor().getFullName(),
                MESSAGE_DATE.format(slot.getDate()), MESSAGE_TIME.format(slot.getStartTime()),
                slot.getLocation().getName(), slot.getRoom());

        String subject = switch (notification.getType()) {
            case CONFIRMATION -> "Tu cita " + appointment.getCode() + " está confirmada";
            case REMINDER -> "Recordatorio de tu cita " + appointment.getCode();
            case RESCHEDULE -> "Tu cita " + appointment.getCode() + " fue reprogramada";
            case CANCELLATION -> "Tu cita " + appointment.getCode() + " fue anulada";
        };
        String body = switch (notification.getType()) {
            case CONFIRMATION, RESCHEDULE -> "Hola %s: tu cita de %s. Llega 15 minutos antes con tu documento."
                    .formatted(patient.getFirstNames(), appointmentDetail);
            case REMINDER -> "MediCitas Anglo: %s, te esperamos para tu cita de %s."
                    .formatted(patient.getFirstNames(), appointmentDetail);
            case CANCELLATION -> "Hola %s: anulamos tu cita de %s. Puedes reservar un nuevo horario en el kiosko."
                    .formatted(patient.getFirstNames(), appointmentDetail);
        };
        return new OutgoingMessage(notification.getChannel(), notification.getDestination(), subject, body);
    }
}
