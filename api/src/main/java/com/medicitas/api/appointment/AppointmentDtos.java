package com.medicitas.api.appointment;

import com.medicitas.api.catalog.Doctor;
import com.medicitas.api.common.Formats;
import com.medicitas.api.schedule.Slot;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public final class AppointmentDtos {

    private static final String SELF_PAY = "Particular";

    private AppointmentDtos() {
    }

    public record AppointmentRequest(
            @NotNull(message = "Falta identificar al paciente.")
            Long patientId,

            @NotNull(message = "Selecciona un horario.")
            Long slotId,

            Long patientInsuranceId,

            @NotBlank(message = "Cuéntanos brevemente el motivo de tu consulta.")
            @Size(max = 250, message = "El motivo de consulta no puede superar los 250 caracteres.")
            String visitReason) {
    }

    public record RescheduleRequest(
            @NotNull(message = "Selecciona el nuevo horario.")
            Long newSlotId) {
    }

    public record CancellationRequest(
            @Size(max = 200, message = "El motivo no puede superar los 200 caracteres.")
            String reason) {
    }

    public record AppointmentResponse(
            Long id,
            String code,
            AppointmentStatus status,
            LocalDate date,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Formats.TIME) LocalTime startTime,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Formats.TIME) LocalTime endTime,
            String specialty,
            String location,
            String room,
            String doctor,
            String coverage,
            String visitReason,
            int rescheduleCount,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Formats.DATE_TIME) OffsetDateTime bookedAt) {

        static AppointmentResponse from(Appointment appointment, ZoneId zone) {
            Slot slot = appointment.getSlot();
            Doctor doctor = slot.getDoctor();
            String coverage = appointment.getPatientInsurance() == null
                    ? SELF_PAY
                    : appointment.getPatientInsurance().getCoverageDescription();
            return new AppointmentResponse(appointment.getId(), appointment.getCode(), appointment.getStatus(),
                    slot.getDate(), slot.getStartTime(), slot.getEndTime(), doctor.getSpecialty().getName(),
                    slot.getLocation().getName(), slot.getRoom(), doctor.getFullName(), coverage,
                    appointment.getVisitReason(), appointment.getRescheduleCount(),
                    appointment.getBookedAt().atZone(zone).toOffsetDateTime());
        }
    }

    public record BookingResult(AppointmentResponse appointment, boolean created) {
    }
}
