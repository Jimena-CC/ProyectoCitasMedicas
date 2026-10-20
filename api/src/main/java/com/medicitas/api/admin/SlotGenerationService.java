package com.medicitas.api.admin;

import com.medicitas.api.admin.AdminSlotDtos.SlotDetail;
import com.medicitas.api.admin.AdminSlotDtos.SlotGenerationRequest;
import com.medicitas.api.admin.AdminSlotDtos.SlotGenerationResult;
import com.medicitas.api.audit.AuditAction;
import com.medicitas.api.audit.AuditService;
import com.medicitas.api.catalog.Doctor;
import com.medicitas.api.catalog.DoctorRepository;
import com.medicitas.api.catalog.Location;
import com.medicitas.api.catalog.LocationRepository;
import com.medicitas.api.catalog.LocationSpecialtyRepository;
import com.medicitas.api.common.BusinessException;
import com.medicitas.api.common.ErrorCodes;
import com.medicitas.api.schedule.Slot;
import com.medicitas.api.schedule.SlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Generación de cupos de agenda para un médico en una sede (CU-023, RF-23). El cálculo de los horarios es
 * una función pura ({@link #plan}) que omite los que se cruzan con cupos ya existentes del médico, de modo que
 * repetir una generación no duplica cupos ({@code uk_slot_doctor_schedule}: médico, fecha y hora de inicio).
 */
@Service
public class SlotGenerationService {

    /** Máximo de días que se pueden generar en una sola llamada. */
    public static final int MAX_RANGE_DAYS = 92;

    /** Días que se generan cuando la petición no indica ninguno. */
    static final Set<DayOfWeek> DEFAULT_WEEKDAYS = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY);

    private static final Logger logger = LoggerFactory.getLogger(SlotGenerationService.class);
    /** Formato de fecha legible para el detalle de auditoría, por ejemplo {@code 21/10/2026}. */
    private static final DateTimeFormatter AUDIT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final DoctorRepository doctorRepository;
    private final LocationRepository locationRepository;
    private final LocationSpecialtyRepository locationSpecialtyRepository;
    private final SlotRepository slotRepository;
    private final AuditService auditService;
    private final Clock clock;

    public SlotGenerationService(DoctorRepository doctorRepository, LocationRepository locationRepository,
                                 LocationSpecialtyRepository locationSpecialtyRepository,
                                 SlotRepository slotRepository, AuditService auditService, Clock clock) {
        this.doctorRepository = doctorRepository;
        this.locationRepository = locationRepository;
        this.locationSpecialtyRepository = locationSpecialtyRepository;
        this.slotRepository = slotRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Horario de un cupo: fecha, hora de inicio y hora de fin.
     *
     * @param date      fecha
     * @param startTime hora de inicio
     * @param endTime   hora de fin
     */
    public record TimeWindow(LocalDate date, LocalTime startTime, LocalTime endTime) {

        /**
         * Indica si dos horarios del mismo día se superponen.
         *
         * @param other otro horario
         * @return {@code true} si comparten al menos un minuto
         */
        public boolean overlaps(TimeWindow other) {
            return date.equals(other.date) && startTime.isBefore(other.endTime) && other.startTime.isBefore(endTime);
        }

        /**
         * Horario de un cupo ya guardado.
         *
         * @param slot cupo
         * @return su horario
         */
        public static TimeWindow of(Slot slot) {
            return new TimeWindow(slot.getDate(), slot.getStartTime(), slot.getEndTime());
        }
    }

    /**
     * Resultado del cálculo: horarios a crear y cuántos se omitieron por cruzarse con cupos existentes.
     *
     * @param toCreate horarios nuevos, ordenados por fecha y hora
     * @param skipped  horarios omitidos
     */
    public record Plan(List<TimeWindow> toCreate, int skipped) {
    }

    /**
     * Calcula los horarios de cupo de un rango de fechas. Cada día incluido en {@code weekdays} se divide en
     * bloques de {@code durationMinutes} desde {@code startTime}; el último bloque termina como máximo en
     * {@code endTime}. Un horario que se cruza con alguno de {@code existing} se omite y se cuenta en {@code skipped}.
     *
     * @param from            primera fecha (inclusive)
     * @param to              última fecha (inclusive)
     * @param startTime       hora del primer cupo
     * @param endTime         hora límite del último cupo
     * @param weekdays        días de la semana a incluir
     * @param durationMinutes duración de cada cupo en minutos, mayor que cero
     * @param existing        horarios que el médico ya tiene en el rango
     * @return el plan de creación
     */
    public static Plan plan(LocalDate from, LocalDate to, LocalTime startTime, LocalTime endTime,
                            Set<DayOfWeek> weekdays, int durationMinutes, Collection<TimeWindow> existing) {
        List<TimeWindow> toCreate = new ArrayList<>();
        int skipped = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (!weekdays.contains(date.getDayOfWeek())) {
                continue;
            }
            for (LocalTime start = startTime; fits(start, durationMinutes, endTime); start = start.plusMinutes(durationMinutes)) {
                TimeWindow candidate = new TimeWindow(date, start, start.plusMinutes(durationMinutes));
                if (existing.stream().anyMatch(candidate::overlaps)) {
                    skipped++;
                } else {
                    toCreate.add(candidate);
                }
            }
        }
        return new Plan(List.copyOf(toCreate), skipped);
    }

    /** Un cupo cabe si termina a más tardar en {@code endTime} sin cruzar la medianoche. */
    private static boolean fits(LocalTime start, int durationMinutes, LocalTime endTime) {
        LocalTime end = start.plusMinutes(durationMinutes);
        return !end.isBefore(start) && !end.isAfter(endTime) && end.isAfter(start);
    }

    /**
     * Genera los cupos libres de un médico en una sede y lo registra en la auditoría.
     *
     * @param request médico, sede, consultorio, rango de fechas, horario, días y duración
     * @return cupos creados, omitidos y el detalle de los creados
     * @throws BusinessException {@code 404 RESOURCE_NOT_FOUND} si el médico o la sede no existen,
     *                           {@code 422 DOCTOR_INACTIVE} si el médico está inactivo,
     *                           {@code 422 SPECIALTY_NOT_OFFERED} si la sede no atiende su especialidad,
     *                           {@code 400 INVALID_DATA} si el rango o el horario no son válidos
     */
    @Transactional
    public SlotGenerationResult generate(SlotGenerationRequest request) {
        Doctor doctor = doctorRepository.findWithSpecialtyById(request.doctorId())
                .orElseThrow(() -> BusinessException.notFound(ErrorCodes.RESOURCE_NOT_FOUND, "El médico no existe."));
        if (!doctor.isActive()) {
            throw BusinessException.ruleViolated(ErrorCodes.DOCTOR_INACTIVE,
                    "El médico está inactivo. Actívalo antes de generar su agenda.");
        }
        Location location = locationRepository.findById(request.locationId())
                .orElseThrow(() -> BusinessException.notFound(ErrorCodes.RESOURCE_NOT_FOUND, "La sede no existe."));
        if (!locationSpecialtyRepository.isOffered(location.getId(), doctor.getSpecialty().getId())) {
            throw BusinessException.ruleViolated(ErrorCodes.SPECIALTY_NOT_OFFERED,
                    "La sede " + location.getName() + " no atiende " + doctor.getSpecialty().getName()
                            + ". Agrega la sede a la especialidad antes de generar la agenda.");
        }
        validateRange(request.from(), request.to());
        if (!request.endTime().isAfter(request.startTime())) {
            throw BusinessException.invalidData("endTime", "La hora de fin debe ser posterior a la hora de inicio.");
        }
        Set<DayOfWeek> weekdays = request.weekdays() == null || request.weekdays().isEmpty()
                ? DEFAULT_WEEKDAYS
                : EnumSet.copyOf(request.weekdays());
        int duration = request.durationMinutes() != null
                ? request.durationMinutes()
                : doctor.getSpecialty().getAppointmentMinutes();
        if (ChronoUnit.MINUTES.between(request.startTime(), request.endTime()) < duration) {
            throw BusinessException.invalidData("endTime",
                    "Entre la hora de inicio y la de fin no cabe ni un cupo de " + duration + " minutos.");
        }

        List<TimeWindow> existing = slotRepository.findByDoctorAndRange(doctor.getId(), request.from(), request.to())
                .stream().map(TimeWindow::of).toList();
        Plan plan = plan(request.from(), request.to(), request.startTime(), request.endTime(), weekdays, duration,
                existing);
        String room = request.room() == null || request.room().isBlank() ? null : request.room().trim();
        List<Slot> slots = plan.toCreate().stream()
                .map(window -> new Slot(doctor, location, window.date(), window.startTime(), window.endTime(), room))
                .toList();
        slotRepository.saveAll(slots);

        auditService.record(AuditAction.SLOTS_GENERATED, null,
                "Se generaron " + slots.size() + " cupos de " + duration + " min para " + doctor.getFullName()
                        + " en " + location.getName() + " del " + AUDIT_DATE.format(request.from()) + " al " + AUDIT_DATE.format(request.to()) + " ("
                        + request.startTime() + " a " + request.endTime() + ")"
                        + (plan.skipped() == 0 ? "" : "; " + plan.skipped() + " omitidos por cruzarse con cupos existentes"));
        logger.info("Agenda generada para el médico {}: {} cupos creados, {} omitidos", doctor.getId(), slots.size(),
                plan.skipped());
        return new SlotGenerationResult(slots.size(), plan.skipped(),
                slots.stream().map(SlotDetail::from).toList());
    }

    /**
     * Valida un rango de fechas de agenda: ordenado, no anterior a hoy y de como máximo {@link #MAX_RANGE_DAYS} días.
     *
     * @param from primera fecha
     * @param to   última fecha
     * @throws BusinessException {@code 400 INVALID_DATA} con el campo que falla
     */
    void validateRange(LocalDate from, LocalDate to) {
        if (from.isBefore(LocalDate.now(clock))) {
            throw BusinessException.invalidData("from", "La fecha inicial no puede ser anterior a hoy.");
        }
        validateOrderedRange(from, to, MAX_RANGE_DAYS);
    }

    /**
     * Valida que {@code from} no sea posterior a {@code to} y que el rango no supere {@code maxDays} días.
     *
     * @param from    primera fecha
     * @param to      última fecha
     * @param maxDays máximo de días, ambos inclusive
     * @throws BusinessException {@code 400 INVALID_DATA} en el campo {@code to}
     */
    static void validateOrderedRange(LocalDate from, LocalDate to, int maxDays) {
        if (to.isBefore(from)) {
            throw BusinessException.invalidData("to", "La fecha final debe ser igual o posterior a la fecha inicial.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > maxDays) {
            throw BusinessException.invalidData("to", "El rango no puede superar los " + maxDays + " días.");
        }
    }
}
