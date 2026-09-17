package com.medicitas.api.patient;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class PatientDtos {

    private PatientDtos() {
    }

    public record PatientRequest(
            @NotNull(message = "Selecciona el tipo de documento.")
            DocumentType documentType,

            @NotBlank(message = "Ingresa tu número de documento.")
            String documentNumber,

            @NotBlank(message = "Ingresa tus nombres.")
            @Size(max = 100, message = "Los nombres no pueden superar los 100 caracteres.")
            String firstNames,

            @NotBlank(message = "Ingresa tus apellidos.")
            @Size(max = 100, message = "Los apellidos no pueden superar los 100 caracteres.")
            String lastNames,

            @NotNull(message = "Ingresa tu fecha de nacimiento.")
            @Past(message = "La fecha de nacimiento debe ser anterior a hoy.")
            LocalDate birthDate,

            @Pattern(regexp = "[MF]", message = "El sexo debe ser M o F.")
            String sex,

            @NotBlank(message = "Ingresa tu número de celular.")
            @Pattern(regexp = "9\\d{8}", message = "El celular debe tener 9 dígitos y empezar con 9.")
            String phone,

            @NotBlank(message = "Ingresa tu correo electrónico.")
            @Email(message = "Ingresa un correo electrónico válido.")
            @Size(max = 120, message = "El correo no puede superar los 120 caracteres.")
            String email,

            Boolean acceptsNotifications) {
    }

    public record InsuranceRequest(
            Long planId,

            @Size(max = 30, message = "El número de póliza no puede superar los 30 caracteres.")
            String policyNumber) {
    }

    public record InsuranceResponse(
            Long patientInsuranceId,
            String insurer,
            String plan,
            String policyNumber,
            CoverageStatus coverageStatus) {
    }

    public record PatientResponse(
            Long id,
            DocumentType documentType,
            String documentNumber,
            String firstNames,
            String lastNames,
            LocalDate birthDate,
            String phone,
            String email,
            InsuranceResponse insurance) {
    }
}
