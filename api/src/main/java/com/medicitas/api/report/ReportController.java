package com.medicitas.api.report;

import com.medicitas.api.report.ReportDtos.AppointmentsReport;
import com.medicitas.api.report.ReportDtos.CsvFile;
import com.medicitas.api.report.ReportDtos.NoShowRateReport;
import com.medicitas.api.report.ReportDtos.OccupancyReport;
import com.medicitas.api.security.ClinicSecurityScheme;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Reportes del administrador (CU-025 a CU-027, RF-24 a RF-26). Solo rol {@code ADMIN}.
 */
@RestController
@RequestMapping("/api/v1/admin/reports")
@Tag(name = "Administración - Reportes", description = "Citas por estado, inasistencia y ocupación, con exportación CSV")
@SecurityRequirement(name = ClinicSecurityScheme.BEARER)
public class ReportController {

    /** Tipo de contenido de la exportación: CSV en UTF-8. */
    static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Citas por estado en el rango, con desglose por especialidad y sede.
     *
     * @param from        primera fecha de cita (inclusive)
     * @param to          última fecha de cita (inclusive)
     * @param specialtyId limita a una especialidad, opcional
     * @param locationId  limita a una sede, opcional
     * @return el reporte
     */
    @GetMapping("/appointments")
    @Operation(summary = "Citas reservadas, atendidas, anuladas y no asistidas por especialidad y sede (RF-24)")
    public AppointmentsReport appointments(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long specialtyId,
            @RequestParam(required = false) Long locationId) {
        return reportService.appointments(from, to, specialtyId, locationId);
    }

    /**
     * Tasa de inasistencia por especialidad.
     *
     * @param from primera fecha de cita (inclusive)
     * @param to   última fecha de cita (inclusive)
     * @return el reporte
     */
    @GetMapping("/no-show-rate")
    @Operation(summary = "Tasa de inasistencia por especialidad: no asistidas / (atendidas + no asistidas) (RF-25)")
    public NoShowRateReport noShowRate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.noShowRate(from, to);
    }

    /**
     * Ocupación por especialidad y sede.
     *
     * @param from primera fecha (inclusive)
     * @param to   última fecha (inclusive)
     * @return el reporte
     */
    @GetMapping("/occupancy")
    @Operation(summary = "Ocupación por especialidad y sede: citas sobre cupos generados (RF-26)")
    public OccupancyReport occupancy(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.occupancy(from, to);
    }

    /**
     * Descarga un reporte en CSV (UTF-8 con BOM, separador coma).
     *
     * @param report {@code appointments}, {@code no-show-rate} u {@code occupancy}
     * @param from   primera fecha (inclusive)
     * @param to     última fecha (inclusive)
     * @return el archivo como adjunto
     */
    @GetMapping("/export")
    @Operation(summary = "Exportar un reporte a CSV")
    public ResponseEntity<byte[]> export(
            @RequestParam String report,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        CsvFile file = reportService.exportCsv(report, from, to);
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(file.content());
    }
}
