package com.medicitas.api.appointment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Optional<Appointment> findByIdempotencyKey(UUID idempotencyKey);

    boolean existsByCode(String code);

    /**
     * Indica si el paciente ya tiene otra cita en el estado dado que se cruza con el horario (RF-08).
     */
    @Query("""
            select case when count(a) > 0 then true else false end
            from Appointment a join a.slot s
            where a.patient.id = :patientId and a.status = :status and a.id <> :excludedId
              and s.date = :date and s.startTime < :endTime and s.endTime > :startTime""")
    boolean existsOverlap(@Param("patientId") Long patientId,
                          @Param("status") AppointmentStatus status,
                          @Param("excludedId") Long excludedId,
                          @Param("date") LocalDate date,
                          @Param("startTime") LocalTime startTime,
                          @Param("endTime") LocalTime endTime);

    @Query("""
            select a from Appointment a
            join fetch a.patient
            join fetch a.slot s join fetch s.doctor d join fetch d.specialty join fetch s.location
            left join fetch a.patientInsurance pi left join fetch pi.plan pl left join fetch pl.insurer
            where a.id = :appointmentId""")
    Optional<Appointment> findDetailById(@Param("appointmentId") Long appointmentId);

    @Query("""
            select a from Appointment a
            join fetch a.patient
            join fetch a.slot s join fetch s.doctor d join fetch d.specialty join fetch s.location
            left join fetch a.patientInsurance pi left join fetch pi.plan pl left join fetch pl.insurer
            where a.patient.id = :patientId and a.status = :status and s.date >= :from
            order by s.date, s.startTime""")
    List<Appointment> findByPatientStatusFrom(@Param("patientId") Long patientId,
                                              @Param("status") AppointmentStatus status,
                                              @Param("from") LocalDate from);

    @Query("""
            select a from Appointment a
            join fetch a.patient
            join fetch a.slot s join fetch s.doctor d join fetch d.specialty join fetch s.location
            left join fetch a.patientInsurance pi left join fetch pi.plan pl left join fetch pl.insurer
            where a.patient.id = :patientId
            order by s.date desc, s.startTime desc""")
    List<Appointment> findAllByPatient(@Param("patientId") Long patientId);
}
