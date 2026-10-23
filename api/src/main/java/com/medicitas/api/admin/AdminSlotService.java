package com.medicitas.api.admin;

import com.medicitas.api.admin.AdminSlotDtos.SlotDetail;
import com.medicitas.api.audit.AuditAction;
import com.medicitas.api.audit.AuditFormats;
import com.medicitas.api.audit.AuditService;
import com.medicitas.api.catalog.DoctorRepository;
import com.medicitas.api.common.BusinessException;
import com.medicitas.api.common.ErrorCodes;
import com.medicitas.api.schedule.Slot;
import com.medicitas.api.schedule.SlotRepository;
import com.medicitas.api.schedule.SlotStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Consulta y bloqueo de cupos por el administrador (CU-023). Un cupo bloqueado no aparece en la disponibilidad
 * del kiosko; solo se puede bloquear un cupo libre (nunca uno reservado) y solo se puede desbloquear uno bloqueado.
 */
@Service
public class AdminSlotService {

    private static final Logger logger = LoggerFactory.getLogger(AdminSlotService.class);

    private final SlotRepository slotRepository;
    private final DoctorRepository doctorRepository;
    private final AuditService auditService;

    public AdminSlotService(SlotRepository slotRepository, DoctorRepository doctorRepository,
                            AuditService auditService) {
        this.slotRepository = slotRepository;
        this.doctorRepository = doctorRepository;
        this.auditService = auditService;
    }

    /**
     * Cupos de un médico en un rango de fechas, en cualquier estado.
     *
     * @param doctorId médico
     * @param from     primera fecha (inclusive)
     * @param to       última fecha (inclusive); como máximo {@link SlotGenerationService#MAX_RANGE_DAYS} días
     * @return los cupos ordenados por fecha y hora
     * @throws BusinessException {@code 404 RESOURCE_NOT_FOUND} si el médico no existe,
     *                           {@code 400 INVALID_DATA} si el rango no es válido
     */
    @Transactional(readOnly = true)
    public List<SlotDetail> list(Long doctorId, LocalDate from, LocalDate to) {
        if (!doctorRepository.existsById(doctorId)) {
            throw BusinessException.notFound(ErrorCodes.RESOURCE_NOT_FOUND, "El médico no existe.");
        }
        SlotGenerationService.validateOrderedRange(from, to, SlotGenerationService.MAX_RANGE_DAYS);
        return slotRepository.findByDoctorAndRange(doctorId, from, to).stream()
                .map(SlotDetail::from)
                .toList();
    }

    /**
     * Bloquea un cupo libre.
     *
     * @param slotId cupo
     * @return el cupo ya bloqueado
     * @throws BusinessException {@code 404 RESOURCE_NOT_FOUND} si no existe,
     *                           {@code 409 SLOT_NOT_FREE} si está reservado o ya bloqueado
     */
    @Transactional
    public SlotDetail block(Long slotId) {
        Slot slot = find(slotId);
        if (slot.getStatus() != SlotStatus.FREE || slotRepository.changeStatus(slotId, SlotStatus.FREE, SlotStatus.BLOCKED) == 0) {
            throw BusinessException.conflict(ErrorCodes.SLOT_NOT_FREE,
                    slot.getStatus() == SlotStatus.BOOKED
                            ? "Este cupo tiene una cita reservada; no se puede bloquear. Reprograma o anula la cita primero."
                            : "Este cupo ya no está libre; actualiza la agenda e inténtalo nuevamente.");
        }
        auditService.record(AuditAction.SLOT_BLOCKED, slotId, "Cupo " + describe(slot) + " bloqueado");
        logger.info("Cupo {} bloqueado", slotId);
        return SlotDetail.from(slot, SlotStatus.BLOCKED);
    }

    /**
     * Desbloquea un cupo bloqueado, que vuelve a estar libre.
     *
     * @param slotId cupo
     * @return el cupo ya libre
     * @throws BusinessException {@code 404 RESOURCE_NOT_FOUND} si no existe,
     *                           {@code 409 SLOT_NOT_BLOCKED} si no está bloqueado
     */
    @Transactional
    public SlotDetail unblock(Long slotId) {
        Slot slot = find(slotId);
        if (slot.getStatus() != SlotStatus.BLOCKED || slotRepository.changeStatus(slotId, SlotStatus.BLOCKED, SlotStatus.FREE) == 0) {
            throw BusinessException.conflict(ErrorCodes.SLOT_NOT_BLOCKED,
                    "Este cupo no está bloqueado; actualiza la agenda e inténtalo nuevamente.");
        }
        auditService.record(AuditAction.SLOT_UNBLOCKED, slotId, "Cupo " + describe(slot) + " desbloqueado");
        logger.info("Cupo {} desbloqueado", slotId);
        return SlotDetail.from(slot, SlotStatus.FREE);
    }

    private Slot find(Long slotId) {
        return slotRepository.findDetailById(slotId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCodes.RESOURCE_NOT_FOUND, "El cupo no existe."));
    }

    /** Cupo en forma legible para la auditoría: fecha, hora de inicio, médico y sede. */
    private static String describe(Slot slot) {
        return "del " + AuditFormats.slot(slot) + " en " + slot.getLocation().getName();
    }
}
