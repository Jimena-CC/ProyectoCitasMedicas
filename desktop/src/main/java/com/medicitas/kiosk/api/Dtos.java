package com.medicitas.kiosk.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Objetos de transferencia que reflejan el contrato REST (docs/api-contract.md).
 */
public final class Dtos {

    private Dtos() {
    }

    public record InsuranceSummary(Long patientInsuranceId, String insurer, String plan,
                                   String policyNumber, String coverageStatus) {
        public String description() {
            return insurer + " · " + plan;
        }

        /**
         * Estado de la cobertura en palabras del paciente, nunca el código de la API (RNF-06).
         *
         * @return {@code "vigente"}, {@code "suspendida"}, {@code "vencida"} o vacío si no se conoce
         */
        public String coverageStatusText() {
            if (coverageStatus == null) {
                return "";
            }
            return switch (coverageStatus) {
                case "ACTIVE" -> "vigente";
                case "SUSPENDED" -> "suspendida";
                case "EXPIRED" -> "vencida";
                default -> "";
            };
        }
    }

    public record Patient(Long id, String documentType, String documentNumber, String firstNames,
                          String lastNames, LocalDate birthDate, String phone, String email,
                          InsuranceSummary insurance) {
        public String firstName() {
            if (firstNames == null || firstNames.isBlank()) {
                return "";
            }
            return firstNames.trim().split("\\s+")[0];
        }

        /** Nombre completo sin mostrar {@code null} cuando falta alguna parte. */
        public String fullName() {
            String first = firstNames == null ? "" : firstNames.trim();
            String last = lastNames == null ? "" : lastNames.trim();
            return (first + " " + last).trim();
        }
    }

    public record NewPatient(String documentType, String documentNumber, String firstNames, String lastNames,
                             LocalDate birthDate, String sex, String phone, String email,
                             boolean acceptsNotifications) {
    }

    public record UpdateInsurance(Long planId, String policyNumber) {
    }

    public record Plan(Long id, String name, BigDecimal consultationCopay) {
    }

    public record Insurer(Long id, String name, List<Plan> plans) {
    }

    public record Location(Long id, String code, String name, String address, String district) {
    }

    public record Specialty(Long id, String name, String description, Integer appointmentMinutes) {
    }

    public record DoctorSummary(Long id, String fullName, String license) {
    }

    public record Slot(Long slotId, LocalTime startTime, LocalTime endTime, String room,
                       DoctorSummary doctor) {
    }

    public record AvailableDay(LocalDate date, int freeSlots, List<Slot> slots) {
    }

    public record Availability(List<AvailableDay> days) {
    }

    public record NewAppointment(Long patientId, Long slotId, Long patientInsuranceId, String visitReason) {
    }

    public record Appointment(Long id, String code, String status, LocalDate date, LocalTime startTime,
                              LocalTime endTime, String specialty, String location, String room, String doctor,
                              String coverage, String visitReason, int rescheduleCount,
                              OffsetDateTime bookedAt) {
    }

    public record Reschedule(Long newSlotId) {
    }

    public record Cancellation(String reason) {
    }

    public record FieldError(String field, String message) {
    }

    public record ApiError(String code, String message, List<FieldError> fields, OffsetDateTime timestamp) {
    }
}
