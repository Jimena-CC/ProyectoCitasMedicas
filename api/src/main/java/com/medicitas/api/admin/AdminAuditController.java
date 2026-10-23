package com.medicitas.api.admin;

import com.medicitas.api.admin.AdminAuditDtos.AuditEntry;
import com.medicitas.api.admin.AdminAuditDtos.PageResponse;
import com.medicitas.api.security.ClinicSecurityScheme;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Consulta del registro de auditoría (CU-024, RF-20). Solo rol {@code ADMIN}.
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
@Tag(name = "Administración - Auditoría", description = "Consulta paginada del registro de auditoría")
@SecurityRequirement(name = ClinicSecurityScheme.BEARER)
public class AdminAuditController {

    private final AdminAuditService adminAuditService;

    public AdminAuditController(AdminAuditService adminAuditService) {
        this.adminAuditService = adminAuditService;
    }

    /**
     * Busca registros de auditoría, del más reciente al más antiguo.
     *
     * @param entity entidad afectada, opcional
     * @param action acción, opcional
     * @param userId usuario responsable, opcional
     * @param from   primera fecha (inclusive), opcional
     * @param to     última fecha (inclusive), opcional
     * @param page   número de página, desde 0
     * @param size   tamaño de página, de 1 a 100
     * @return la página de registros
     */
    @GetMapping
    @Operation(summary = "Consultar la auditoría con filtros por entidad, acción, usuario y fechas (RF-20)")
    public PageResponse<AuditEntry> search(
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminAuditService.search(entity, action, userId, from, to, page, size);
    }
}
