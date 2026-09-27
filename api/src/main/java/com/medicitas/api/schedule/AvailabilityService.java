package com.medicitas.api.schedule;

import com.medicitas.api.catalog.LocationRepository;
import com.medicitas.api.catalog.SpecialtyRepository;
import com.medicitas.api.common.BusinessException;
import com.medicitas.api.common.ErrorCodes;
import com.medicitas.api.schedule.AvailabilityDtos.AvailabilityResponse;
import com.medicitas.api.schedule.AvailabilityDtos.AvailableDay;
import com.medicitas.api.schedule.AvailabilityDtos.DoctorSummary;
import com.medicitas.api.schedule.AvailabilityDtos.SlotResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AvailabilityService {

    static final int MAX_DAYS = 14;

    private final SlotRepository slotRepository;
    private final SpecialtyRepository specialtyRepository;
    private final LocationRepository locationRepository;
    private final Clock clock;

    public AvailabilityService(SlotRepository slotRepository, SpecialtyRepository specialtyRepository,
                               LocationRepository locationRepository, Clock clock) {
        this.slotRepository = slotRepository;
        this.specialtyRepository = specialtyRepository;
        this.locationRepository = locationRepository;
        this.clock = clock;
    }

    /**
     * Cupos libres y futuros de una especialidad en una sede (RF-05). Incluye los días sin cupos
     * para que el kiosko pinte la franja de fechas completa.
     */
    @Transactional(readOnly = true)
    public AvailabilityResponse query(Long specialtyId, Long locationId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw BusinessException.invalidData("to", "La fecha final debe ser igual o posterior a la fecha inicial.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw BusinessException.invalidData("to", "Puedes consultar como máximo 14 días a la vez.");
        }
        if (!specialtyRepository.existsById(specialtyId)) {
            throw BusinessException.notFound(ErrorCodes.RESOURCE_NOT_FOUND, "La especialidad seleccionada no existe.");
        }
        if (!locationRepository.existsById(locationId)) {
            throw BusinessException.notFound(ErrorCodes.RESOURCE_NOT_FOUND, "La sede seleccionada no existe.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Map<LocalDate, List<SlotResponse>> slotsByDate = slotRepository
                .findBySpecialtyLocationAndRange(specialtyId, locationId, SlotStatus.FREE, from, to).stream()
                .filter(slot -> slot.getStart().isAfter(now))
                .collect(Collectors.groupingBy(Slot::getDate,
                        Collectors.mapping(AvailabilityService::toResponse, Collectors.toList())));

        List<AvailableDay> days = from.datesUntil(to.plusDays(1))
                .map(date -> {
                    List<SlotResponse> slots = slotsByDate.getOrDefault(date, List.of());
                    return new AvailableDay(date, slots.size(), slots);
                })
                .toList();
        return new AvailabilityResponse(days);
    }

    private static SlotResponse toResponse(Slot slot) {
        return new SlotResponse(slot.getId(), slot.getStartTime(), slot.getEndTime(), slot.getRoom(),
                new DoctorSummary(slot.getDoctor().getId(), slot.getDoctor().getFullName(), slot.getDoctor().getLicense()));
    }
}
