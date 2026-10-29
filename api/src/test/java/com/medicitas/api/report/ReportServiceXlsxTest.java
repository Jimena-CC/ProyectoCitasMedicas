package com.medicitas.api.report;

import com.medicitas.api.appointment.AppointmentRepository;
import com.medicitas.api.appointment.AppointmentStatus;
import com.medicitas.api.catalog.LocationRepository;
import com.medicitas.api.catalog.SpecialtyRepository;
import com.medicitas.api.common.BusinessException;
import com.medicitas.api.report.ReportDtos.AppointmentFact;
import com.medicitas.api.report.ReportDtos.AppointmentsReport;
import com.medicitas.api.report.ReportDtos.NoShowRateReport;
import com.medicitas.api.report.ReportDtos.XlsxFile;
import com.medicitas.api.schedule.SlotRepository;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Exportación a Excel de los reportes: debe llevar las mismas filas y cifras que el CSV.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Exportación Excel de reportes")
class ReportServiceXlsxTest {

    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 31);

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private SlotRepository slotRepository;
    @Mock
    private SpecialtyRepository specialtyRepository;
    @Mock
    private LocationRepository locationRepository;

    private static XSSFWorkbook open(byte[] bytes) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    private static AppointmentFact fact(long specialtyId, String specialty, long locationId, String location,
                                        AppointmentStatus status) {
        return new AppointmentFact(specialtyId, specialty, locationId, location, status);
    }

    @Test
    @DisplayName("El Excel de citas tiene las mismas filas y cifras que el CSV")
    void citasXlsxCoincideConElCsv() throws IOException {
        AppointmentsReport report = ReportService.summarizeAppointments(FROM, TO, List.of(
                fact(3L, "Ginecología, Obstetricia", 1L, "Sede San Isidro", AppointmentStatus.ATTENDED),
                fact(3L, "Ginecología, Obstetricia", 1L, "Sede San Isidro", AppointmentStatus.NO_SHOW),
                fact(1L, "Cardiología", 2L, "Sede La Molina", AppointmentStatus.BOOKED)));

        long csvLines = new String(ReportService.toCsv(report), StandardCharsets.UTF_8).lines().count();

        try (XSSFWorkbook workbook = open(ReportService.toXlsx(report))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(workbook.getSheetName(0)).isEqualTo("Citas por estado");
            assertThat((long) sheet.getPhysicalNumberOfRows()).isEqualTo(csvLines);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("grupo");
            assertThat(sheet.getRow(0).getCell(6).getStringCellValue()).isEqualTo("total");
            // Cardiología (orden alfabético) va primero: 1 reservada
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Cardiología");
            assertThat(sheet.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(1.0);
            // Fila final: total general de 3 citas
            int last = sheet.getLastRowNum();
            assertThat(sheet.getRow(last).getCell(0).getStringCellValue()).isEqualTo("Total");
            assertThat(sheet.getRow(last).getCell(6).getNumericCellValue()).isEqualTo(3.0);
        }
    }

    @Test
    @DisplayName("La tasa de inasistencia se guarda como número, no como texto")
    void tasaDeInasistenciaEsNumerica() throws IOException {
        NoShowRateReport report = ReportService.summarizeNoShow(FROM, TO, List.of(
                fact(1L, "Cardiología", 1L, "Sede San Isidro", AppointmentStatus.ATTENDED),
                fact(1L, "Cardiología", 1L, "Sede San Isidro", AppointmentStatus.NO_SHOW)));

        try (XSSFWorkbook workbook = open(ReportService.toXlsx(report))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(workbook.getSheetName(0)).isEqualTo("Tasa de inasistencia");
            assertThat(sheet.getRow(1).getCell(3).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(1).getCell(3).getNumericCellValue()).isEqualTo(0.5);
        }
    }

    @Test
    @DisplayName("exportXlsx consulta el rango y nombra el archivo con el reporte, las fechas y la extensión .xlsx")
    void exportaConNombreDescriptivo() throws IOException {
        when(appointmentRepository.findWithDetailBySlotDateBetween(FROM, TO)).thenReturn(List.of());
        ReportService service = new ReportService(appointmentRepository, slotRepository, specialtyRepository,
                locationRepository);

        XlsxFile file = service.exportXlsx("no-show-rate", FROM, TO, null, null);

        verify(appointmentRepository).findWithDetailBySlotDateBetween(FROM, TO);
        assertThat(file.fileName()).isEqualTo("reporte-inasistencia_2026-10-01_2026-10-31.xlsx");
        try (XSSFWorkbook workbook = open(file.content())) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("especialidad");
            assertThat(sheet.getRow(sheet.getLastRowNum()).getCell(0).getStringCellValue()).isEqualTo("Total");
        }
    }

    @Test
    @DisplayName("Rechaza un reporte desconocido sin consultar la base")
    void rechazaUnReporteDesconocido() {
        ReportService service = new ReportService(appointmentRepository, slotRepository, specialtyRepository,
                locationRepository);

        assertThatThrownBy(() -> service.exportXlsx("ventas", FROM, TO, null, null))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(appointmentRepository, slotRepository, specialtyRepository, locationRepository);
    }

    @Test
    @DisplayName("Rechaza una especialidad inexistente en el filtro del reporte de citas")
    void rechazaUnaEspecialidadInexistente() {
        when(specialtyRepository.existsById(999L)).thenReturn(false);
        ReportService service = new ReportService(appointmentRepository, slotRepository, specialtyRepository,
                locationRepository);

        assertThatThrownBy(() -> service.exportXlsx("appointments", FROM, TO, 999L, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getStatus().value()).isEqualTo(404));
    }
}
