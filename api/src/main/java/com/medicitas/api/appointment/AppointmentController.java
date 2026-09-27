package com.medicitas.api.appointment;

import com.medicitas.api.appointment.AppointmentDtos.AppointmentRequest;
import com.medicitas.api.appointment.AppointmentDtos.AppointmentResponse;
import com.medicitas.api.appointment.AppointmentDtos.BookingResult;
import com.medicitas.api.appointment.AppointmentDtos.CancellationRequest;
import com.medicitas.api.appointment.AppointmentDtos.RescheduleRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Citas", description = "Reserva, consulta, reprogramación y anulación de citas")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping("/appointments")
    @Operation(summary = "Reservar un cupo (RF-06, RF-07, RF-08). Reintentar con la misma Idempotency-Key devuelve la misma cita")
    public ResponseEntity<AppointmentResponse> book(
            @Parameter(description = "UUID generado por el kiosko para cada intento de reserva")
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody AppointmentRequest request) {
        BookingResult result = appointmentService.book(request, idempotencyKey);
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/v1/appointments/" + result.appointment().id()))
                    .body(result.appointment());
        }
        return ResponseEntity.ok(result.appointment());
    }

    @GetMapping("/patients/{id}/appointments")
    @Operation(summary = "Listar las citas del paciente; por defecto solo las reservadas y futuras")
    public List<AppointmentResponse> listByPatient(@PathVariable("id") Long patientId,
                                                   @RequestParam(defaultValue = "true") boolean upcoming) {
        return appointmentService.listByPatient(patientId, upcoming);
    }

    @PatchMapping("/appointments/{id}/reschedule")
    @Operation(summary = "Reprogramar una cita a otro cupo de la misma especialidad (RF-09)")
    public AppointmentResponse reschedule(@PathVariable Long id, @Valid @RequestBody RescheduleRequest request) {
        return appointmentService.reschedule(id, request);
    }

    @PatchMapping("/appointments/{id}/cancellation")
    @Operation(summary = "Anular una cita reservada (RF-10)")
    public AppointmentResponse cancel(@PathVariable Long id,
                                      @Valid @RequestBody(required = false) CancellationRequest request) {
        return appointmentService.cancel(id, request == null ? null : request.reason());
    }
}
