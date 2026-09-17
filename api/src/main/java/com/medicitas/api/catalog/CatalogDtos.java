package com.medicitas.api.catalog;

import java.math.BigDecimal;
import java.util.List;

/**
 * Respuestas de los catálogos consumidos por el kiosko (RF-03, RF-04).
 */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record LocationResponse(Long id, String code, String name, String address, String district) {
    }

    public record SpecialtyResponse(Long id, String name, String description, Integer appointmentMinutes) {
    }

    public record PlanResponse(Long id, String name, BigDecimal consultationCopay) {
    }

    public record InsurerResponse(Long id, String name, List<PlanResponse> plans) {
    }
}
