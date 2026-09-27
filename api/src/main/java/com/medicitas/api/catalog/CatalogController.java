package com.medicitas.api.catalog;

import com.medicitas.api.catalog.CatalogDtos.InsurerResponse;
import com.medicitas.api.catalog.CatalogDtos.LocationResponse;
import com.medicitas.api.catalog.CatalogDtos.SpecialtyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Catálogos", description = "Sedes, especialidades y aseguradoras con convenio")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/locations")
    @Operation(summary = "Listar sedes activas")
    public List<LocationResponse> listLocations() {
        return catalogService.listLocations();
    }

    @GetMapping("/specialties")
    @Operation(summary = "Listar especialidades activas, opcionalmente filtradas por sede (RF-04)")
    public List<SpecialtyResponse> listSpecialties(@RequestParam(required = false) Long locationId) {
        return catalogService.listSpecialties(locationId);
    }

    @GetMapping("/insurers")
    @Operation(summary = "Listar aseguradoras con convenio y sus planes (RF-03)")
    public List<InsurerResponse> listInsurers() {
        return catalogService.listInsurers();
    }
}
