package com.medicitas.api.frontdesk;

import com.medicitas.api.appointment.AppointmentDtos.AppointmentRequest;
import com.medicitas.api.appointment.AppointmentDtos.AppointmentResponse;
import com.medicitas.api.appointment.AppointmentDtos.BookingResult;
import com.medicitas.api.appointment.AppointmentDtos.RescheduleRequest;
import com.medicitas.api.frontdesk.FrontDeskDtos.FrontDeskCancellationRequest;
import com.medicitas.api.frontdesk.FrontDeskDtos.InsuranceValidationResponse;
import com.medicitas.api.patient.DocumentType;
import com.medicitas.api.patient.PatientDtos.InsuranceRequest;
import com.medicitas.api.patient.PatientDtos.PatientRequest;
import com.medicitas.api.patient.PatientDtos.PatientResponse;
import com.medicitas.api.security.ClinicSecurityScheme;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Ventanilla de admisión (roles {@code FRONT_DESK} y {@code ADMIN}): pacientes, seguros y citas en nombre
 * del paciente. La disponibilidad se consulta con el endpoint público {@code GET /api/v1/availability}.
 */
@RestController
@RequestMapping("/api/v1/front-desk")
@Tag(name = "Admisión", description = "Búsqueda y registro de pacientes, validación de seguro y citas desde ventanilla")
@SecurityRequirement(name = ClinicSecurityScheme.BEARER)
public class FrontDeskController {

    private final FrontDeskService frontDeskService;

    public FrontDeskController(FrontDeskService frontDeskService) {
        this.frontDeskService = frontDeskService;
    }

    /**
     * Busca un paciente por documento.
     *
     * @param documentType   tipo de documento
     * @param documentNumber número de documento
     * @return el paciente con su cobertura vigente
     */
    @GetMapping("/patients")
    @Operation(summary = "Buscar un paciente por documento (CU-013)")
    public PatientResponse searchPatient(@RequestParam DocumentType documentType,
                                        @RequestParam String documentNumber) {
        return frontDeskService.searchPatient(documentType, documentNumber);
    }

    /**
     * Registra un paciente nuevo.
     *
     * @param request datos del paciente
     * @return {@code 201} con el paciente creado
     */
    @PostMapping("/patients")
    @Operation(summary = "Registrar un paciente desde ventanilla, con las validaciones del kiosko (RF-01)")
    public ResponseEntity<PatientResponse> registerPatient(@Valid @RequestBody PatientRequest request) {
        PatientResponse patient = frontDeskService.registerPatient(request);
        return ResponseEntity.created(URI.create("/api/v1/front-desk/patients/" + patient.id())).body(patient);
    }

    /**
     * Valida la aseguradora del paciente.
     *
     * @param id paciente
     * @return sus coberturas con el resultado de la validación
     */
    @GetMapping("/patients/{id}/insurance")
    @Operation(summary = "Validar la aseguradora del paciente: coberturas activas y vigentes, o atención particular (CU-014)")
    public InsuranceValidationResponse validateInsurance(@PathVariable Long id) {
        return frontDeskService.validateInsurance(id);
    }

    /**
     * Asocia o actualiza el seguro del paciente.
     *
     * @param id      paciente
     * @param request plan y número de póliza; sin {@code planId} queda como particular
     * @return el paciente con su cobertura vigente
     */
    @PutMapping("/patients/{id}/insurance")
    @Operation(summary = "Asociar o actualizar el seguro del paciente, o dejarlo como particular (RF-03)")
    public PatientResponse updateInsurance(@PathVariable Long id, @Valid @RequestBody InsuranceRequest request) {
        return frontDeskService.updateInsurance(id, request);
    }

    /**
     * Citas del paciente.
     *
     * @param patientId paciente
     * @param upcoming  {@code true} para solo las reservadas y futuras
     * @return las citas
     */
    @GetMapping("/patients/{id}/appointments")
    @Operation(summary = "Listar las citas del paciente; por defecto solo las reservadas y futuras")
    public List<AppointmentResponse> listAppointments(@PathVariable("id") Long patientId,
                                                      @RequestParam(defaultValue = "true") boolean upcoming) {
        return frontDeskService.listAppointments(patientId, upcoming);
    }

    /**
     * Reserva una cita en nombre del paciente.
     *
     * @param idempotencyKey clave del intento
     * @param request        datos de la reserva
     * @return {@code 201} con la cita, o {@code 200} si la clave ya fue usada
     */
    @PostMapping("/appointments")
    @Operation(summary = "Reservar un cupo desde ventanilla con canal FRONT_DESK (CU-015, RF-07, RF-08)")
    public ResponseEntity<AppointmentResponse> book(
            @Parameter(description = "UUID generado por el panel para cada intento de reserva")
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody AppointmentRequest request) {
        BookingResult result = frontDeskService.book(request, idempotencyKey);
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/v1/appointments/" + result.appointment().id()))
                    .body(result.appointment());
        }
        return ResponseEntity.ok(result.appointment());
    }

    /**
     * Reprograma una cita.
     *
     * @param id      cita
     * @param request nuevo cupo
     * @return la cita actualizada
     */
    @PatchMapping("/appointments/{id}/reschedule")
    @Operation(summary = "Reprogramar una cita a otro cupo de la misma especialidad (CU-016, RF-09)")
    public AppointmentResponse reschedule(@PathVariable Long id, @Valid @RequestBody RescheduleRequest request) {
        return frontDeskService.reschedule(id, request);
    }

    /**
     * Anula una cita con motivo obligatorio.
     *
     * @param id      cita
     * @param request motivo de la anulación
     * @return la cita anulada
     */
    @PatchMapping("/appointments/{id}/cancellation")
    @Operation(summary = "Anular una cita indicando el motivo (CU-016, RF-10)")
    public AppointmentResponse cancel(@PathVariable Long id, @Valid @RequestBody FrontDeskCancellationRequest request) {
        return frontDeskService.cancel(id, request.reason());
    }
}
