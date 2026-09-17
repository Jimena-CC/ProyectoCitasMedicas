package com.medicitas.api.appointment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppointmentHistoryRepository extends JpaRepository<AppointmentHistory, Long> {

    List<AppointmentHistory> findByAppointmentIdOrderByIdAsc(Long appointmentId);
}
