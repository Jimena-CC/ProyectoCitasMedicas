package com.medicitas.api.catalog;

import com.medicitas.api.catalog.CatalogDtos.InsurerResponse;
import com.medicitas.api.catalog.CatalogDtos.LocationResponse;
import com.medicitas.api.catalog.CatalogDtos.PlanResponse;
import com.medicitas.api.catalog.CatalogDtos.SpecialtyResponse;
import com.medicitas.api.common.BusinessException;
import com.medicitas.api.common.ErrorCodes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final LocationRepository locationRepository;
    private final SpecialtyRepository specialtyRepository;
    private final InsurerRepository insurerRepository;

    public CatalogService(LocationRepository locationRepository, SpecialtyRepository specialtyRepository,
                          InsurerRepository insurerRepository) {
        this.locationRepository = locationRepository;
        this.specialtyRepository = specialtyRepository;
        this.insurerRepository = insurerRepository;
    }

    public List<LocationResponse> listLocations() {
        return locationRepository.findByActiveTrueOrderByIdAsc().stream()
                .map(location -> new LocationResponse(location.getId(), location.getCode(), location.getName(),
                        location.getAddress(), location.getDistrict()))
                .toList();
    }

    public List<SpecialtyResponse> listSpecialties(Long locationId) {
        List<Specialty> specialties;
        if (locationId == null) {
            specialties = specialtyRepository.findByActiveTrueOrderByNameAsc();
        } else {
            if (!locationRepository.existsById(locationId)) {
                throw BusinessException.notFound(ErrorCodes.RESOURCE_NOT_FOUND, "La sede seleccionada no existe.");
            }
            specialties = specialtyRepository.findActiveByLocation(locationId);
        }
        return specialties.stream()
                .map(specialty -> new SpecialtyResponse(specialty.getId(), specialty.getName(),
                        specialty.getDescription(), specialty.getAppointmentMinutes().intValue()))
                .toList();
    }

    public List<InsurerResponse> listInsurers() {
        return insurerRepository.findActiveWithPlans().stream()
                .map(insurer -> new InsurerResponse(insurer.getId(), insurer.getName(),
                        insurer.getPlans().stream()
                                .filter(InsurancePlan::isActive)
                                .sorted(Comparator.comparing(InsurancePlan::getName))
                                .map(plan -> new PlanResponse(plan.getId(), plan.getName(), plan.getConsultationCopay()))
                                .toList()))
                .toList();
    }
}
