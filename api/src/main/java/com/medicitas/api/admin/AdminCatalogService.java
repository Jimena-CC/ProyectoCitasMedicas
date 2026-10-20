package com.medicitas.api.admin;

import com.medicitas.api.admin.AdminCatalogDtos.DoctorDetail;
import com.medicitas.api.admin.AdminCatalogDtos.DoctorRequest;
import com.medicitas.api.admin.AdminCatalogDtos.LocationDetail;
import com.medicitas.api.admin.AdminCatalogDtos.LocationRef;
import com.medicitas.api.admin.AdminCatalogDtos.SpecialtyDetail;
import com.medicitas.api.admin.AdminCatalogDtos.SpecialtyRef;
import com.medicitas.api.admin.AdminCatalogDtos.SpecialtyRequest;
import com.medicitas.api.audit.AuditAction;
import com.medicitas.api.audit.AuditService;
import com.medicitas.api.catalog.Doctor;
import com.medicitas.api.catalog.DoctorRepository;
import com.medicitas.api.catalog.Location;
import com.medicitas.api.catalog.LocationRepository;
import com.medicitas.api.catalog.LocationSpecialty;
import com.medicitas.api.catalog.LocationSpecialtyRepository;
import com.medicitas.api.catalog.Specialty;
import com.medicitas.api.catalog.SpecialtyRepository;
import com.medicitas.api.common.BusinessException;
import com.medicitas.api.common.ErrorCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Administración del catálogo (CU-021, CU-022): sedes en lectura, y alta y edición de especialidades y médicos.
 * Lo que aquí se desactiva deja de verse en el kiosko: {@code CatalogService} solo lista especialidades activas
 * (y relaciones sede-especialidad activas) y {@code AvailabilityService} solo muestra cupos de médicos activos
 * de especialidades que la sede sigue atendiendo.
 */
@Service
public class AdminCatalogService {

    private static final Logger logger = LoggerFactory.getLogger(AdminCatalogService.class);

    private final LocationRepository locationRepository;
    private final SpecialtyRepository specialtyRepository;
    private final LocationSpecialtyRepository locationSpecialtyRepository;
    private final DoctorRepository doctorRepository;
    private final AuditService auditService;

    public AdminCatalogService(LocationRepository locationRepository, SpecialtyRepository specialtyRepository,
                               LocationSpecialtyRepository locationSpecialtyRepository,
                               DoctorRepository doctorRepository, AuditService auditService) {
        this.locationRepository = locationRepository;
        this.specialtyRepository = specialtyRepository;
        this.locationSpecialtyRepository = locationSpecialtyRepository;
        this.doctorRepository = doctorRepository;
        this.auditService = auditService;
    }

    /**
     * Lista todas las sedes, activas e inactivas, ordenadas por id.
     *
     * @return las sedes para los formularios del administrador
     */
    @Transactional(readOnly = true)
    public List<LocationDetail> listLocations() {
        return locationRepository.findAll(Sort.by("id")).stream()
                .map(location -> new LocationDetail(location.getId(), location.getCode(), location.getName(),
                        location.getAddress(), location.getDistrict(), location.getPhone(), location.isActive()))
                .toList();
    }

    /**
     * Lista todas las especialidades, activas e inactivas, con las sedes que las atienden.
     *
     * @return las especialidades ordenadas por nombre
     */
    @Transactional(readOnly = true)
    public List<SpecialtyDetail> listSpecialties() {
        Map<Long, List<LocationRef>> locationsBySpecialty = locationSpecialtyRepository
                .findAllWithLocationAndSpecialty().stream()
                .filter(LocationSpecialty::isActive)
                .collect(Collectors.groupingBy(relation -> relation.getSpecialty().getId(),
                        Collectors.mapping(relation -> toRef(relation.getLocation()), Collectors.toList())));
        return specialtyRepository.findAllByOrderByNameAsc().stream()
                .map(specialty -> toDetail(specialty, locationsBySpecialty.getOrDefault(specialty.getId(), List.of())))
                .toList();
    }

    /**
     * Crea una especialidad activa y la asocia a las sedes indicadas.
     *
     * @param request datos de la especialidad
     * @return la especialidad creada
     * @throws BusinessException {@code 409 SPECIALTY_NAME_TAKEN} si el nombre ya existe,
     *                           {@code 404 RESOURCE_NOT_FOUND} si alguna sede no existe
     */
    @Transactional
    public SpecialtyDetail createSpecialty(SpecialtyRequest request) {
        String name = request.name().trim();
        if (specialtyRepository.existsByNameIgnoreCase(name)) {
            throw specialtyNameTaken();
        }
        List<Location> locations = findLocations(request.locationIds());
        Specialty specialty = specialtyRepository.save(
                new Specialty(name, blankToNull(request.description()), request.appointmentMinutes().shortValue()));
        for (Location location : locations) {
            locationSpecialtyRepository.save(new LocationSpecialty(location, specialty));
        }
        auditService.record(AuditAction.SPECIALTY_CREATED, specialty.getId(),
                "Especialidad " + specialty.getName() + " creada (" + specialty.getAppointmentMinutes()
                        + " min por cita; sedes: " + describe(locations) + ")");
        logger.info("Especialidad {} creada con {} sedes", specialty.getName(), locations.size());
        return toDetail(specialty, locations.stream().map(AdminCatalogService::toRef).toList());
    }

    /**
     * Edita una especialidad, sus sedes y su estado. Las relaciones con sedes que ya no figuran en la lista
     * quedan inactivas (no se borran, para conservar el historial de cupos y citas).
     *
     * @param id      identificador de la especialidad
     * @param request nuevos valores
     * @return la especialidad actualizada
     * @throws BusinessException {@code 404 RESOURCE_NOT_FOUND} si no existe la especialidad o una sede,
     *                           {@code 409 SPECIALTY_NAME_TAKEN} si otra especialidad ya usa el nombre
     */
    @Transactional
    public SpecialtyDetail updateSpecialty(Long id, SpecialtyRequest request) {
        Specialty specialty = specialtyRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound(ErrorCodes.RESOURCE_NOT_FOUND,
                        "La especialidad no existe."));
        String name = request.name().trim();
        if (specialtyRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw specialtyNameTaken();
        }
        List<Location> locations = findLocations(request.locationIds());
        specialty.update(name, blankToNull(request.description()), request.appointmentMinutes().shortValue());
        if (request.active() != null) {
            specialty.setActive(request.active());
        }

        Set<Long> wantedIds = locations.stream().map(Location::getId).collect(Collectors.toSet());
        Set<Long> existingIds = new LinkedHashSet<>();
        for (LocationSpecialty relation : locationSpecialtyRepository.findBySpecialtyId(id)) {
            existingIds.add(relation.getLocation().getId());
            relation.setActive(wantedIds.contains(relation.getLocation().getId()));
        }
        for (Location location : locations) {
            if (!existingIds.contains(location.getId())) {
                locationSpecialtyRepository.save(new LocationSpecialty(location, specialty));
            }
        }
        auditService.record(AuditAction.SPECIALTY_UPDATED, specialty.getId(),
                "Especialidad " + specialty.getName() + " actualizada (" + specialty.getAppointmentMinutes()
                        + " min por cita; " + (specialty.isActive() ? "activa" : "inactiva") + "; sedes: "
                        + describe(locations) + ")");
        logger.info("Especialidad {} actualizada; activa {}", specialty.getName(), specialty.isActive());
        return toDetail(specialty, locations.stream().map(AdminCatalogService::toRef).toList());
    }

    /**
     * Lista todos los médicos, activos e inactivos, con su especialidad.
     *
     * @return los médicos ordenados por apellidos
     */
    @Transactional(readOnly = true)
    public List<DoctorDetail> listDoctors() {
        return doctorRepository.findAllWithSpecialty().stream()
                .map(AdminCatalogService::toDetail)
                .toList();
    }

    /**
     * Crea un médico activo.
     *
     * @param request datos del médico
     * @return el médico creado
     * @throws BusinessException {@code 409 DOCTOR_LICENSE_TAKEN} si la colegiatura ya existe,
     *                           {@code 404 RESOURCE_NOT_FOUND} si la especialidad no existe
     */
    @Transactional
    public DoctorDetail createDoctor(DoctorRequest request) {
        String license = request.license().trim();
        if (doctorRepository.existsByLicenseIgnoreCase(license)) {
            throw doctorLicenseTaken();
        }
        Specialty specialty = findSpecialty(request.specialtyId());
        Doctor doctor = doctorRepository.save(new Doctor(license, blankToNull(request.specialistRegistry()),
                request.firstNames().trim(), request.lastNames().trim(), specialty));
        auditService.record(AuditAction.DOCTOR_CREATED, doctor.getId(),
                "Médico " + doctor.getFullName() + " (CMP " + doctor.getLicense() + ") creado en " + specialty.getName());
        logger.info("Médico {} creado en {}", doctor.getFullName(), specialty.getName());
        return toDetail(doctor);
    }

    /**
     * Edita los datos, la especialidad y el estado de un médico.
     *
     * @param id      identificador del médico
     * @param request nuevos valores
     * @return el médico actualizado
     * @throws BusinessException {@code 404 RESOURCE_NOT_FOUND} si no existe el médico o la especialidad,
     *                           {@code 409 DOCTOR_LICENSE_TAKEN} si otro médico ya usa la colegiatura
     */
    @Transactional
    public DoctorDetail updateDoctor(Long id, DoctorRequest request) {
        Doctor doctor = doctorRepository.findWithSpecialtyById(id)
                .orElseThrow(() -> BusinessException.notFound(ErrorCodes.RESOURCE_NOT_FOUND, "El médico no existe."));
        String license = request.license().trim();
        if (doctorRepository.existsByLicenseIgnoreCaseAndIdNot(license, id)) {
            throw doctorLicenseTaken();
        }
        Specialty specialty = findSpecialty(request.specialtyId());
        doctor.update(license, blankToNull(request.specialistRegistry()), request.firstNames().trim(),
                request.lastNames().trim(), specialty);
        if (request.active() != null) {
            doctor.setActive(request.active());
        }
        auditService.record(AuditAction.DOCTOR_UPDATED, doctor.getId(),
                "Médico " + doctor.getFullName() + " (CMP " + doctor.getLicense() + ") actualizado: "
                        + specialty.getName() + ", " + (doctor.isActive() ? "activo" : "inactivo"));
        logger.info("Médico {} actualizado; activo {}", doctor.getFullName(), doctor.isActive());
        return toDetail(doctor);
    }

    private List<Location> findLocations(List<Long> locationIds) {
        List<Location> locations = new ArrayList<>();
        for (Long locationId : new LinkedHashSet<>(locationIds)) {
            locations.add(locationRepository.findById(locationId)
                    .orElseThrow(() -> BusinessException.notFound(ErrorCodes.RESOURCE_NOT_FOUND,
                            "La sede " + locationId + " no existe.")));
        }
        locations.sort(Comparator.comparing(Location::getId));
        return locations;
    }

    private Specialty findSpecialty(Long specialtyId) {
        return specialtyRepository.findById(specialtyId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCodes.RESOURCE_NOT_FOUND,
                        "La especialidad seleccionada no existe."));
    }

    private static BusinessException specialtyNameTaken() {
        return BusinessException.conflict(ErrorCodes.SPECIALTY_NAME_TAKEN,
                "Ya existe una especialidad con ese nombre.");
    }

    private static BusinessException doctorLicenseTaken() {
        return BusinessException.conflict(ErrorCodes.DOCTOR_LICENSE_TAKEN,
                "Ya existe un médico con ese número de colegiatura.");
    }

    private static String describe(List<Location> locations) {
        return locations.isEmpty() ? "ninguna"
                : locations.stream().map(Location::getName).collect(Collectors.joining(", "));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static LocationRef toRef(Location location) {
        return new LocationRef(location.getId(), location.getCode(), location.getName());
    }

    private static SpecialtyDetail toDetail(Specialty specialty, List<LocationRef> locations) {
        return new SpecialtyDetail(specialty.getId(), specialty.getName(), specialty.getDescription(),
                specialty.getAppointmentMinutes().intValue(), specialty.isActive(), locations);
    }

    private static DoctorDetail toDetail(Doctor doctor) {
        return new DoctorDetail(doctor.getId(), doctor.getLicense(), doctor.getSpecialistRegistry(),
                doctor.getFirstNames(), doctor.getLastNames(), doctor.getFullName(),
                new SpecialtyRef(doctor.getSpecialty().getId(), doctor.getSpecialty().getName()), doctor.isActive());
    }
}
