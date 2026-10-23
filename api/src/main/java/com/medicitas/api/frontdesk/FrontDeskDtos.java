package com.medicitas.api.frontdesk;

import com.medicitas.api.patient.CoverageStatus;
import com.medicitas.api.patient.PatientInsurance;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Cuerpos propios del módulo de admisión. Las operaciones sobre pacientes y citas reutilizan los records
 * de {@code PatientDtos} y {@code AppointmentDtos} del kiosko.
 */
public final class FrontDeskDtos {

    private FrontDeskDtos() {
    }

    /**
     * Anulación desde ventanilla: a diferencia del kiosko, el motivo es obligatorio (CU-016).
     *
     * @param reason motivo de la anulación
     */
    public record FrontDeskCancellationRequest(
            @NotBlank(message = "Indica el motivo de la anulación.")
            @Size(max = 200, message = "El motivo no puede superar los 200 caracteres.")
            String reason) {
    }

    /**
     * Una cobertura del paciente con el resultado de su validación.
     *
     * @param patientInsuranceId id de la cobertura, el que se envía como {@code patientInsuranceId} al reservar
     * @param insurer            aseguradora
     * @param plan               plan
     * @param policyNumber       número de póliza, si se registró
     * @param coverageStatus     estado registrado de la cobertura
     * @param validUntil         fecha de vencimiento, o nulo si no vence
     * @param valid              {@code true} solo si está activa y vigente a la fecha de hoy
     */
    public record CoverageValidation(Long patientInsuranceId, String insurer, String plan, String policyNumber,
                                     CoverageStatus coverageStatus, LocalDate validUntil, boolean valid) {

        static CoverageValidation of(PatientInsurance insurance, LocalDate today) {
            return new CoverageValidation(insurance.getId(), insurance.getPlan().getInsurer().getName(),
                    insurance.getPlan().getName(), insurance.getPolicyNumber(), insurance.getCoverageStatus(),
                    insurance.getValidUntil(), insurance.isActiveOn(today));
        }
    }

    /**
     * Resultado de validar la aseguradora de un paciente (RF-24, CU-014).
     *
     * @param patientId paciente validado
     * @param valid     {@code true} si tiene al menos una cobertura activa y vigente
     * @param message   explicación para ventanilla, en español
     * @param coverages todas las coberturas registradas, vigentes primero
     */
    public record InsuranceValidationResponse(Long patientId, boolean valid, String message,
                                              List<CoverageValidation> coverages) {
    }
}
