package com.medicitas.api.doctor;

import com.medicitas.api.doctor.DoctorDtos.AgendaItemResponse;
import com.medicitas.api.doctor.DoctorDtos.AgendaResponse;
import com.medicitas.api.doctor.DoctorDtos.AttendanceRequest;
import com.medicitas.api.security.ClinicSecurityScheme;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Panel del médico (solo rol {@code DOCTOR}): agenda del día y registro de asistencia.
 */
@RestController
@RequestMapping("/api/v1/doctor")
@Tag(name = "Médico", description = "Agenda del día del médico autenticado y registro de asistencia")
@SecurityRequirement(name = ClinicSecurityScheme.BEARER)
public class DoctorAgendaController {

    private final DoctorAgendaService doctorAgendaService;

    public DoctorAgendaController(DoctorAgendaService doctorAgendaService) {
        this.doctorAgendaService = doctorAgendaService;
    }

    /**
     * Agenda del médico autenticado.
     *
     * @param date fecha consultada; por defecto hoy en la zona horaria de la clínica
     * @return la agenda con su resumen y sus citas en orden de hora
     */
    @GetMapping("/agenda")
    @Operation(summary = "Consultar la agenda del médico autenticado; por defecto la de hoy (RF-20, CU-011)")
    public AgendaResponse agenda(
            @Parameter(description = "Fecha en formato YYYY-MM-DD; por defecto hoy")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return doctorAgendaService.agendaOfDay(date);
    }

    /**
     * Registra si el paciente fue atendido o no se presentó.
     *
     * @param id      cita del médico autenticado
     * @param request resultado y nota opcional
     * @return el item de agenda actualizado
     */
    @PatchMapping("/appointments/{id}/attendance")
    @Operation(summary = "Registrar asistencia o inasistencia de una cita reservada de hoy o de días pasados (RF-21, CU-012)")
    public AgendaItemResponse registerAttendance(@PathVariable Long id, @Valid @RequestBody AttendanceRequest request) {
        return doctorAgendaService.registerAttendance(id, request);
    }
}
