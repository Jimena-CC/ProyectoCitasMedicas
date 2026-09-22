package com.medicitas.api.schedule;

import com.medicitas.api.common.Formats;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class AvailabilityDtos {

    private AvailabilityDtos() {
    }

    public record DoctorSummary(Long id, String fullName, String license) {
    }

    public record SlotResponse(
            Long slotId,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Formats.TIME) LocalTime startTime,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Formats.TIME) LocalTime endTime,
            String room,
            DoctorSummary doctor) {
    }

    public record AvailableDay(LocalDate date, int freeSlots, List<SlotResponse> slots) {
    }

    public record AvailabilityResponse(List<AvailableDay> days) {
    }
}
