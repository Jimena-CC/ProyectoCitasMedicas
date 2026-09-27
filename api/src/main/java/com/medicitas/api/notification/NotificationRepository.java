package com.medicitas.api.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByAppointmentIdOrderByScheduledForAscIdAsc(Long appointmentId);

    @Query("""
            select n.id from Notification n
            where n.status = :status and n.scheduledFor <= :limit
            order by n.scheduledFor, n.id""")
    List<Long> findIdsByStatusScheduledUntil(@Param("status") NotificationStatus status,
                                             @Param("limit") Instant limit);

    @Query("""
            select n from Notification n
            join fetch n.appointment a join fetch a.patient
            join fetch a.slot s join fetch s.doctor d join fetch d.specialty join fetch s.location
            where n.id = :notificationId""")
    Optional<Notification> findDetailById(@Param("notificationId") Long notificationId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Notification n set n.status = :cancelled
            where n.appointment.id = :appointmentId and n.type = :type and n.status = :pending""")
    int cancelPending(@Param("appointmentId") Long appointmentId,
                      @Param("type") NotificationType type,
                      @Param("pending") NotificationStatus pending,
                      @Param("cancelled") NotificationStatus cancelled);
}
