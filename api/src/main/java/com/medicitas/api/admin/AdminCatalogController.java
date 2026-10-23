package com.medicitas.api.admin;

import com.medicitas.api.admin.AdminCatalogDtos.DoctorDetail;
import com.medicitas.api.admin.AdminCatalogDtos.DoctorRequest;
import com.medicitas.api.admin.AdminCatalogDtos.LocationDetail;
import com.medicitas.api.admin.AdminCatalogDtos.SpecialtyDetail;
import com.medicitas.api.admin.AdminCatalogDtos.SpecialtyRequest;
import com.medicitas.api.security.ClinicSecurityScheme;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Administración del catálogo: sedes (lectura), especialidades y médicos (CU-021, CU-022). Solo rol {@code ADMIN}.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Administración - Catálogo", description = "Sedes, especialidades y médicos")
@SecurityRequirement(name = ClinicSecurityScheme.BEARER)
public class AdminCatalogController {

    private final AdminCatalogService adminCatalogService;

    public AdminCatalogController(AdminCatalogService adminCatalogService) {
        this.adminCatalogService = adminCatalogService;
    }

    /**
     * Lista todas las sedes, incluidas las inactivas.
     *
     * @return las sedes
     */
    @GetMapping("/locations")
    @Operation(summary = "Listar todas las sedes para los formularios de administración")
    public List<LocationDetail> listLocations() {
        return adminCatalogService.listLocations();
    }

    /**
     * Lista todas las especialidades con sus sedes, incluidas las inactivas.
     *
     * @return las especialidades
     */
    @GetMapping("/specialties")
    @Operation(summary = "Listar todas las especialidades con sus sedes (RF-21)")
    public List<SpecialtyDetail> listSpecialties() {
        return adminCatalogService.listSpecialties();
    }

    /**
     * Crea una especialidad.
     *
     * @param request nombre, descripción, duración de la cita y sedes
     * @return {@code 201 Created} con la especialidad
     */
    @PostMapping("/specialties")
    @Operation(summary = "Crear una especialidad (RF-21)")
    public ResponseEntity<SpecialtyDetail> createSpecialty(@Valid @RequestBody SpecialtyRequest request) {
        SpecialtyDetail specialty = adminCatalogService.createSpecialty(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/specialties/" + specialty.id())).body(specialty);
    }

    /**
     * Edita una especialidad, sus sedes y su estado.
     *
     * @param id      identificador de la especialidad
     * @param request nuevos valores
     * @return la especialidad actualizada
     */
    @PutMapping("/specialties/{id}")
    @Operation(summary = "Editar una especialidad, sus sedes y su estado (RF-21)")
    public SpecialtyDetail updateSpecialty(@PathVariable Long id, @Valid @RequestBody SpecialtyRequest request) {
        return adminCatalogService.updateSpecialty(id, request);
    }

    /**
     * Lista todos los médicos, incluidos los inactivos.
     *
     * @return los médicos con su especialidad
     */
    @GetMapping("/doctors")
    @Operation(summary = "Listar todos los médicos con su especialidad y estado (RF-22)")
    public List<DoctorDetail> listDoctors() {
        return adminCatalogService.listDoctors();
    }

    /**
     * Crea un médico.
     *
     * @param request colegiatura, registro de especialista, nombres, apellidos y especialidad
     * @return {@code 201 Created} con el médico
     */
    @PostMapping("/doctors")
    @Operation(summary = "Crear un médico (RF-22)")
    public ResponseEntity<DoctorDetail> createDoctor(@Valid @RequestBody DoctorRequest request) {
        DoctorDetail doctor = adminCatalogService.createDoctor(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/doctors/" + doctor.id())).body(doctor);
    }

    /**
     * Edita los datos, la especialidad y el estado de un médico.
     *
     * @param id      identificador del médico
     * @param request nuevos valores
     * @return el médico actualizado
     */
    @PutMapping("/doctors/{id}")
    @Operation(summary = "Editar un médico y su estado (RF-22)")
    public DoctorDetail updateDoctor(@PathVariable Long id, @Valid @RequestBody DoctorRequest request) {
        return adminCatalogService.updateDoctor(id, request);
    }
}
