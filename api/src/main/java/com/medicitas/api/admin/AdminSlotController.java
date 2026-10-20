package com.medicitas.api.admin;

import com.medicitas.api.admin.AdminSlotDtos.SlotDetail;
import com.medicitas.api.admin.AdminSlotDtos.SlotGenerationRequest;
import com.medicitas.api.admin.AdminSlotDtos.SlotGenerationResult;
import com.medicitas.api.security.ClinicSecurityScheme;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Gestión de agendas: generación, consulta y bloqueo de cupos por médico (CU-023, RF-23). Solo rol {@code ADMIN}.
 */
@RestController
@RequestMapping("/api/v1/admin/slots")
@Tag(name = "Administración - Agendas", description = "Generación y bloqueo de cupos por médico y sede")
@SecurityRequirement(name = ClinicSecurityScheme.BEARER)
public class AdminSlotController {

    private final SlotGenerationService slotGenerationService;
    private final AdminSlotService adminSlotService;

    public AdminSlotController(SlotGenerationService slotGenerationService, AdminSlotService adminSlotService) {
        this.slotGenerationService = slotGenerationService;
        this.adminSlotService = adminSlotService;
    }

    /**
     * Cupos de un médico en un rango de fechas, con su estado.
     *
     * @param doctorId médico
     * @param from     primera fecha (inclusive)
     * @param to       última fecha (inclusive)
     * @return los cupos ordenados por fecha y hora
     */
    @GetMapping
    @Operation(summary = "Consultar los cupos de un médico en un rango de fechas, con su estado")
    public List<SlotDetail> list(@RequestParam Long doctorId,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return adminSlotService.list(doctorId, from, to);
    }

    /**
     * Genera cupos libres para un médico en una sede sin duplicar los existentes.
     *
     * @param request médico, sede, consultorio, rango de fechas, horario, días y duración
     * @return {@code 201 Created} con los cupos creados y los omitidos
     */
    @PostMapping("/generate")
    @Operation(summary = "Generar la agenda de un médico en una sede (RF-23)")
    public ResponseEntity<SlotGenerationResult> generate(@Valid @RequestBody SlotGenerationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(slotGenerationService.generate(request));
    }

    /**
     * Bloquea un cupo libre para que no se pueda reservar.
     *
     * @param id cupo
     * @return el cupo bloqueado
     */
    @PatchMapping("/{id}/block")
    @Operation(summary = "Bloquear un cupo libre")
    public SlotDetail block(@PathVariable Long id) {
        return adminSlotService.block(id);
    }

    /**
     * Desbloquea un cupo bloqueado.
     *
     * @param id cupo
     * @return el cupo ya libre
     */
    @PatchMapping("/{id}/unblock")
    @Operation(summary = "Desbloquear un cupo bloqueado")
    public SlotDetail unblock(@PathVariable Long id) {
        return adminSlotService.unblock(id);
    }
}
