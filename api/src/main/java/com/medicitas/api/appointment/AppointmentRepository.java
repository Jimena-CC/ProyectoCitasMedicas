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

    // Issue #45: módulos de médico y admisión
    /**
     * Citas de un médico en una fecha, con paciente, cupo, sede y seguro ya cargados, ordenadas por hora de inicio.
     * Incluye todos los estados para que la agenda muestre también las anuladas y las ya atendidas.
     *
     * @param doctorId médico
     * @param date     fecha del cupo
     * @return las citas del día en orden de hora
     */
    @Query("""
            select a from Appointment a
            join fetch a.patient
            join fetch a.slot s join fetch s.doctor d join fetch d.specialty join fetch s.location
            left join fetch a.patientInsurance pi left join fetch pi.plan pl left join fetch pl.insurer
            where d.id = :doctorId and s.date = :date
            order by s.startTime, a.id""")
    List<Appointment> findByDoctorAndDate(@Param("doctorId") Long doctorId, @Param("date") LocalDate date);

    // Issue #46: reportes

    /**
     * Citas cuyo cupo cae en el rango de fechas, en cualquier estado, con médico, especialidad y sede cargados.
     *
     * @param from primera fecha de cita (inclusive)
     * @param to   última fecha de cita (inclusive)
     * @return las citas del rango
     */
    @Query("""
            select a from Appointment a
            join fetch a.slot s join fetch s.doctor d join fetch d.specialty join fetch s.location
            where s.date between :from and :to""")
    List<Appointment> findWithDetailBySlotDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
