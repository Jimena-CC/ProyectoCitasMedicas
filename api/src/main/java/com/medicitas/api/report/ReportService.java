package com.medicitas.api.report;

import com.medicitas.api.appointment.AppointmentRepository;
import com.medicitas.api.appointment.AppointmentStatus;
import com.medicitas.api.catalog.LocationRepository;
import com.medicitas.api.catalog.SpecialtyRepository;
import com.medicitas.api.common.BusinessException;
import com.medicitas.api.common.ErrorCodes;
import com.medicitas.api.report.ReportDtos.AppointmentFact;
import com.medicitas.api.report.ReportDtos.AppointmentsReport;
import com.medicitas.api.report.ReportDtos.CsvFile;
import com.medicitas.api.report.ReportDtos.LocationStatusRow;
import com.medicitas.api.report.ReportDtos.NoShowRateReport;
import com.medicitas.api.report.ReportDtos.NoShowRow;
import com.medicitas.api.report.ReportDtos.NoShowTotals;
import com.medicitas.api.report.ReportDtos.OccupancyReport;
import com.medicitas.api.report.ReportDtos.OccupancyRow;
import com.medicitas.api.report.ReportDtos.OccupancyTotals;
import com.medicitas.api.report.ReportDtos.SlotFact;
import com.medicitas.api.report.ReportDtos.SpecialtyStatusRow;
import com.medicitas.api.report.ReportDtos.StatusTotals;
import com.medicitas.api.schedule.SlotRepository;
import com.medicitas.api.schedule.SlotStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Reportes del administrador (CU-025 a CU-027, RF-24 a RF-26): citas por estado, tasa de inasistencia y
 * ocupación, con exportación a CSV.
 *
 * <p>Decisión de implementación: las cifras se calculan <strong>en memoria</strong> sobre las citas y los cupos
 * del rango (una consulta con {@code join fetch} cada uno) en lugar de con consultas JPQL agregadas. El rango
 * está acotado a {@value #MAX_RANGE_DAYS} días y el volumen de una clínica de dos sedes es de miles de filas,
 * no de millones; a cambio, un único recorrido alimenta los tres reportes y el CSV con exactamente las mismas
 * cifras, y las funciones de cálculo ({@link #summarizeAppointments}, {@link #summarizeNoShow} y
 * {@link #summarizeOccupancy}) son puras y se prueban sin base de datos.
 */
@Service
public class ReportService {

    /** Máximo de días que abarca un reporte (un año, incluido el bisiesto). */
    public static final int MAX_RANGE_DAYS = 366;

    /** Estados de cita que ocupan un cupo: reservada, atendida o no asistida. */
    static final Set<AppointmentStatus> OCCUPYING_STATUSES =
            Set.of(AppointmentStatus.BOOKED, AppointmentStatus.ATTENDED, AppointmentStatus.NO_SHOW);

    private static final int RATE_SCALE = 4;

    /**
     * Reportes exportables, identificados por el valor del parámetro {@code report}.
     */
    public enum ReportKind {

        /** Citas por estado. */
        APPOINTMENTS("appointments", "reporte-citas"),

        /** Tasa de inasistencia por especialidad. */
        NO_SHOW_RATE("no-show-rate", "reporte-inasistencia"),

        /** Ocupación por especialidad y sede. */
        OCCUPANCY("occupancy", "reporte-ocupacion");

        private final String param;
        private final String filePrefix;

        ReportKind(String param, String filePrefix) {
            this.param = param;
            this.filePrefix = filePrefix;
        }

        /**
         * Valor del parámetro {@code report} de la exportación.
         *
         * @return el identificador en la URL
         */
        public String param() {
            return param;
        }

        /**
         * Resuelve el reporte a partir del parámetro {@code report}.
         *
         * @param value valor recibido, por ejemplo {@code no-show-rate}
         * @return el reporte
         * @throws BusinessException {@code 400 INVALID_DATA} si no corresponde a ningún reporte
         */
        public static ReportKind fromParam(String value) {
            for (ReportKind kind : values()) {
                if (kind.param.equalsIgnoreCase(value == null ? "" : value.trim())) {
                    return kind;
                }
            }
            throw BusinessException.invalidData("report",
                    "El reporte debe ser appointments, no-show-rate u occupancy.");
        }
    }

    private final AppointmentRepository appointmentRepository;
    private final SlotRepository slotRepository;
    private final SpecialtyRepository specialtyRepository;
    private final LocationRepository locationRepository;

    public ReportService(AppointmentRepository appointmentRepository, SlotRepository slotRepository,
                         SpecialtyRepository specialtyRepository, LocationRepository locationRepository) {
        this.appointmentRepository = appointmentRepository;
        this.slotRepository = slotRepository;
        this.specialtyRepository = specialtyRepository;
        this.locationRepository = locationRepository;
    }

    /**
     * Citas por estado en el rango, con desglose por especialidad y sede (RF-24).
     *
     * @param from        primera fecha de cita (inclusive)
     * @param to          última fecha de cita (inclusive)
     * @param specialtyId limita el reporte a una especialidad, opcional
     * @param locationId  limita el reporte a una sede, opcional
     * @return el reporte
     * @throws BusinessException {@code 400 INVALID_DATA} si el rango no es válido,
     *                           {@code 404 RESOURCE_NOT_FOUND} si la especialidad o la sede no existen
     */
    @Transactional(readOnly = true)
    public AppointmentsReport appointments(LocalDate from, LocalDate to, Long specialtyId, Long locationId) {
        validateRange(from, to);
        if (specialtyId != null && !specialtyRepository.existsById(specialtyId)) {
            throw BusinessException.notFound(ErrorCodes.RESOURCE_NOT_FOUND, "La especialidad seleccionada no existe.");
        }
        if (locationId != null && !locationRepository.existsById(locationId)) {
            throw BusinessException.notFound(ErrorCodes.RESOURCE_NOT_FOUND, "La sede seleccionada no existe.");
        }
        List<AppointmentFact> facts = appointmentFacts(from, to).stream()
                .filter(fact -> specialtyId == null || fact.specialtyId().equals(specialtyId))
                .filter(fact -> locationId == null || fact.locationId().equals(locationId))
                .toList();
        return summarizeAppointments(from, to, facts);
    }

    /**
     * Tasa de inasistencia por especialidad en el rango (RF-25).
     *
     * @param from primera fecha de cita (inclusive)
     * @param to   última fecha de cita (inclusive)
     * @return el reporte
     * @throws BusinessException {@code 400 INVALID_DATA} si el rango no es válido
     */
    @Transactional(readOnly = true)
    public NoShowRateReport noShowRate(LocalDate from, LocalDate to) {
        validateRange(from, to);
        return summarizeNoShow(from, to, appointmentFacts(from, to));
    }

    /**
     * Ocupación por especialidad y sede en el rango (RF-26).
     *
     * @param from primera fecha (inclusive)
     * @param to   última fecha (inclusive)
     * @return el reporte
     * @throws BusinessException {@code 400 INVALID_DATA} si el rango no es válido
     */
    @Transactional(readOnly = true)
    public OccupancyReport occupancy(LocalDate from, LocalDate to) {
        validateRange(from, to);
        List<SlotFact> slots = slotRepository.findWithDetailByRange(from, to).stream().map(SlotFact::from).toList();
        return summarizeOccupancy(from, to, slots, appointmentFacts(from, to));
    }

    /**
     * Exporta un reporte a CSV con las mismas cifras que su versión JSON.
     *
     * @param report {@code appointments}, {@code no-show-rate} u {@code occupancy}
     * @param from   primera fecha (inclusive)
     * @param to     última fecha (inclusive)
     * @return el archivo con su nombre sugerido
     * @throws BusinessException {@code 400 INVALID_DATA} si el reporte o el rango no son válidos
     */
    @Transactional(readOnly = true)
    public CsvFile exportCsv(String report, LocalDate from, LocalDate to) {
        ReportKind kind = ReportKind.fromParam(report);
        byte[] content = switch (kind) {
            case APPOINTMENTS -> toCsv(appointments(from, to, null, null));
            case NO_SHOW_RATE -> toCsv(noShowRate(from, to));
            case OCCUPANCY -> toCsv(occupancy(from, to));
        };
        return new CsvFile(kind.filePrefix + "_" + from + "_" + to + ".csv", content);
    }

    /**
     * Valida que {@code from} no sea posterior a {@code to} y que el rango no supere {@link #MAX_RANGE_DAYS} días.
     *
     * @param from primera fecha
     * @param to   última fecha
     * @throws BusinessException {@code 400 INVALID_DATA} en el campo {@code to}
     */
    static void validateRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw BusinessException.invalidData("to", "La fecha final debe ser igual o posterior a la fecha inicial.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw BusinessException.invalidData("to", "El reporte puede abarcar como máximo " + MAX_RANGE_DAYS + " días.");
        }
    }

    private List<AppointmentFact> appointmentFacts(LocalDate from, LocalDate to) {
        return appointmentRepository.findWithDetailBySlotDateBetween(from, to).stream()
                .map(AppointmentFact::from)
                .toList();
    }

    /**
     * Cuenta las citas por estado en total, por especialidad y por sede.
     *
     * @param from  primera fecha del rango (solo se copia al reporte)
     * @param to    última fecha del rango (solo se copia al reporte)
     * @param facts citas del rango
     * @return el reporte con los desgloses ordenados por nombre
     */
    public static AppointmentsReport summarizeAppointments(LocalDate from, LocalDate to, List<AppointmentFact> facts) {
        StatusTotals totals = StatusTotals.EMPTY;
        Map<String, SpecialtyStatusRow> bySpecialty = new TreeMap<>();
        Map<String, LocationStatusRow> byLocation = new TreeMap<>();
        for (AppointmentFact fact : facts) {
            totals = totals.plus(fact.status());
            bySpecialty.merge(sortKey(fact.specialtyName(), fact.specialtyId()),
                    new SpecialtyStatusRow(fact.specialtyId(), fact.specialtyName(), StatusTotals.EMPTY.plus(fact.status())),
                    (row, ignored) -> new SpecialtyStatusRow(row.specialtyId(), row.specialtyName(), row.totals().plus(fact.status())));
            byLocation.merge(sortKey(fact.locationName(), fact.locationId()),
                    new LocationStatusRow(fact.locationId(), fact.locationName(), StatusTotals.EMPTY.plus(fact.status())),
                    (row, ignored) -> new LocationStatusRow(row.locationId(), row.locationName(), row.totals().plus(fact.status())));
        }
        return new AppointmentsReport(from, to, totals, List.copyOf(bySpecialty.values()), List.copyOf(byLocation.values()));
    }

    /**
     * Calcula la tasa de inasistencia por especialidad: {@code noShow / (attended + noShow)}; las citas
     * reservadas o anuladas no cuentan. Una especialidad sin atenciones ni inasistencias no aparece.
     *
     * @param from  primera fecha del rango (solo se copia al reporte)
     * @param to    última fecha del rango (solo se copia al reporte)
     * @param facts citas del rango
     * @return el reporte con las filas ordenadas por nombre de especialidad
     */
    public static NoShowRateReport summarizeNoShow(LocalDate from, LocalDate to, List<AppointmentFact> facts) {
        Map<String, long[]> counters = new TreeMap<>();
        Map<String, AppointmentFact> samples = new LinkedHashMap<>();
        long attended = 0;
        long noShow = 0;
        for (AppointmentFact fact : facts) {
            if (fact.status() != AppointmentStatus.ATTENDED && fact.status() != AppointmentStatus.NO_SHOW) {
                continue;
            }
            String key = sortKey(fact.specialtyName(), fact.specialtyId());
            long[] counter = counters.computeIfAbsent(key, ignored -> new long[2]);
            samples.putIfAbsent(key, fact);
            if (fact.status() == AppointmentStatus.ATTENDED) {
                counter[0]++;
                attended++;
            } else {
                counter[1]++;
                noShow++;
            }
        }
        List<NoShowRow> rows = counters.entrySet().stream()
                .map(entry -> {
                    AppointmentFact sample = samples.get(entry.getKey());
                    long[] counter = entry.getValue();
                    return new NoShowRow(sample.specialtyId(), sample.specialtyName(), counter[0], counter[1],
                            rate(counter[1], counter[0] + counter[1]));
                })
                .toList();
        return new NoShowRateReport(from, to, rows, new NoShowTotals(attended, noShow, rate(noShow, attended + noShow)));
    }

    /**
     * Calcula la ocupación por especialidad y sede: citas reservadas, atendidas o no asistidas sobre los cupos
     * generados en el rango, sin contar los bloqueados (que se informan aparte).
     *
     * @param from         primera fecha del rango (solo se copia al reporte)
     * @param to           última fecha del rango (solo se copia al reporte)
     * @param slots        cupos del rango, en cualquier estado
     * @param appointments citas del rango, en cualquier estado
     * @return el reporte con las filas ordenadas por especialidad y sede
     */
    public static OccupancyReport summarizeOccupancy(LocalDate from, LocalDate to, List<SlotFact> slots,
                                                     List<AppointmentFact> appointments) {
        Map<String, long[]> counters = new TreeMap<>();
        Map<String, SlotFact> samples = new LinkedHashMap<>();
        Function<SlotFact, String> keyOf = slot -> sortKey(slot.specialtyName(), slot.specialtyId())
                + "|" + sortKey(slot.locationName(), slot.locationId());
        for (SlotFact slot : slots) {
            String key = keyOf.apply(slot);
            long[] counter = counters.computeIfAbsent(key, ignored -> new long[3]);
            samples.putIfAbsent(key, slot);
            if (slot.status() == SlotStatus.BLOCKED) {
                counter[1]++;
            } else {
                counter[0]++;
            }
        }
        for (AppointmentFact appointment : appointments) {
            if (!OCCUPYING_STATUSES.contains(appointment.status())) {
                continue;
            }
            String key = sortKey(appointment.specialtyName(), appointment.specialtyId())
                    + "|" + sortKey(appointment.locationName(), appointment.locationId());
            long[] counter = counters.computeIfAbsent(key, ignored -> new long[3]);
            samples.putIfAbsent(key, new SlotFact(appointment.specialtyId(), appointment.specialtyName(),
                    appointment.locationId(), appointment.locationName(), null));
            counter[2]++;
        }
        long slotsTotal = 0;
        long slotsBlocked = 0;
        long slotsBooked = 0;
        List<OccupancyRow> rows = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : counters.entrySet()) {
            SlotFact sample = samples.get(entry.getKey());
            long[] counter = entry.getValue();
            slotsTotal += counter[0];
            slotsBlocked += counter[1];
            slotsBooked += counter[2];
            rows.add(new OccupancyRow(sample.specialtyId(), sample.specialtyName(), sample.locationId(),
                    sample.locationName(), counter[0], counter[1], counter[2], rate(counter[2], counter[0])));
        }
        return new OccupancyReport(from, to, List.copyOf(rows),
                new OccupancyTotals(slotsTotal, slotsBlocked, slotsBooked, rate(slotsBooked, slotsTotal)));
    }

    /**
     * Proporción con cuatro decimales; 0 si el denominador es 0.
     *
     * @param numerator   numerador
     * @param denominator denominador
     * @return la tasa entre 0 y 1
     */
    static double rate(long numerator, long denominator) {
        if (denominator == 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), RATE_SCALE, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * CSV del reporte de citas: una fila por especialidad, una por sede y la fila total.
     *
     * @param report reporte calculado
     * @return el archivo
     */
    static byte[] toCsv(AppointmentsReport report) {
        CsvWriter csv = new CsvWriter()
                .row("grupo", "nombre", "reservadas", "atendidas", "anuladas", "inasistencias", "total");
        for (SpecialtyStatusRow row : report.bySpecialty()) {
            appendTotals(csv, "Especialidad", row.specialtyName(), row.totals());
        }
        for (LocationStatusRow row : report.byLocation()) {
            appendTotals(csv, "Sede", row.locationName(), row.totals());
        }
        appendTotals(csv, "Total", "Todas", report.totals());
        return csv.toBytes();
    }

    /**
     * CSV de la tasa de inasistencia: una fila por especialidad y la fila total.
     *
     * @param report reporte calculado
     * @return el archivo
     */
    static byte[] toCsv(NoShowRateReport report) {
        CsvWriter csv = new CsvWriter().row("especialidad", "atendidas", "inasistencias", "tasa_inasistencia");
        for (NoShowRow row : report.bySpecialty()) {
            csv.row(row.specialtyName(), row.attended(), row.noShow(), row.rate());
        }
        csv.row("Total", report.total().attended(), report.total().noShow(), report.total().rate());
        return csv.toBytes();
    }

    /**
     * CSV de la ocupación: una fila por especialidad y sede y la fila total.
     *
     * @param report reporte calculado
     * @return el archivo
     */
    static byte[] toCsv(OccupancyReport report) {
        CsvWriter csv = new CsvWriter()
                .row("especialidad", "sede", "cupos", "cupos_bloqueados", "cupos_ocupados", "tasa_ocupacion");
        for (OccupancyRow row : report.rows()) {
            csv.row(row.specialtyName(), row.locationName(), row.slotsTotal(), row.slotsBlocked(), row.slotsBooked(),
                    row.rate());
        }
        csv.row("Total", "Todas", report.total().slotsTotal(), report.total().slotsBlocked(),
                report.total().slotsBooked(), report.total().rate());
        return csv.toBytes();
    }

    private static void appendTotals(CsvWriter csv, String group, String name, StatusTotals totals) {
        csv.row(group, name, totals.booked(), totals.attended(), totals.cancelled(), totals.noShow(), totals.total());
    }

    /** Clave de orden por nombre y, a igual nombre, por id. */
    private static String sortKey(String name, Long id) {
        return name + "#" + id;
    }
}
