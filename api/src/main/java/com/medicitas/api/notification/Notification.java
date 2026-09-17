package com.medicitas.api.notification;

import com.medicitas.api.appointment.Appointment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

/**
 * Mensaje programado hacia el Servicio de Mensajería, con su estado de entrega (RF-12, RF-13).
 */
@Entity
@Table(name = "notification")
public class Notification {

    private static final int MAX_DETAIL = 250;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 15)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 5)
    private NotificationChannel channel;

    @Column(name = "destination", nullable = false, length = 120)
    private String destination;

    @Column(name = "scheduled_for", nullable = false)
    private Instant scheduledFor;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private Short attempts = 0;

    @Column(name = "provider_message_id", length = 80)
    private String providerMessageId;

    @Column(name = "error_detail", length = MAX_DETAIL)
    private String errorDetail;

    protected Notification() {
    }

    public static Notification create(Appointment appointment, NotificationType type, NotificationChannel channel,
                                      String destination, Instant scheduledFor) {
        Notification notification = new Notification();
        notification.appointment = appointment;
        notification.type = type;
        notification.channel = channel;
        notification.destination = destination;
        notification.scheduledFor = scheduledFor;
        return notification;
    }

    public void markSent(Instant now, String providerMessageId) {
        this.attempts = (short) (attempts + 1);
        this.status = NotificationStatus.SENT;
        this.sentAt = now;
        this.providerMessageId = providerMessageId;
        this.errorDetail = null;
    }

    /**
     * Registra un intento fallido: reprograma el reintento o marca FAILED al agotar los intentos.
     */
    public void recordFailure(Instant now, String detail, int maxAttempts, Duration retryWait) {
        this.attempts = (short) (attempts + 1);
        this.errorDetail = detail == null ? null : detail.substring(0, Math.min(detail.length(), MAX_DETAIL));
        if (attempts >= maxAttempts) {
            this.status = NotificationStatus.FAILED;
        } else {
            this.scheduledFor = now.plus(retryWait);
        }
    }

    public void cancel() {
        this.status = NotificationStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public NotificationType getType() {
        return type;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public String getDestination() {
        return destination;
    }

    public Instant getScheduledFor() {
        return scheduledFor;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public Short getAttempts() {
        return attempts;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public String getErrorDetail() {
        return errorDetail;
    }
}
