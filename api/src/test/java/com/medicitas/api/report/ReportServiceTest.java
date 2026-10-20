package com.medicitas.api.report;

import com.medicitas.api.appointment.AppointmentRepository;
import com.medicitas.api.appointment.AppointmentStatus;
import com.medicitas.api.catalog.LocationRepository;
import com.medicitas.api.catalog.SpecialtyRepository;
import com.medicitas.api.common.BusinessException;
import com.medicitas.api.report.ReportDtos.AppointmentFact;
import com.medicitas.api.report.ReportDtos.AppointmentsReport;
import com.medicitas.api.report.ReportDtos.CsvFile;
import com.medicitas.api.report.ReportDtos.NoShowRateReport;
import com.medicitas.api.report.ReportDtos.OccupancyReport;
import com.medicitas.api.report.ReportDtos.OccupancyRow;
import com.medicitas.api.report.ReportDtos.SlotFact;
import com.medicitas.api.report.ReportDtos.SpecialtyStatusRow;
import com.medicitas.api.report.ReportDtos.StatusTotals;
import com.medicitas.api.report.ReportService.ReportKind;
import com.medicitas.api.schedule.SlotRepository;
import com.medicitas.api.schedule.SlotStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cálculos puros de {@link ReportService} sobre hechos construidos a mano, validación del rango y forma del CSV.
 * Las cifras contra la base real se comprueban en {@code ReportApiTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReportService")
class ReportServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 31);

    private static final long CARDIOLOGY = 1L;
    private static final long DERMATOLOGY = 2L;
    private static final long SAN_ISIDRO = 10L;
    private static final long LA_MOLINA = 20L;

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private SlotRepository slotRepository;
    @Mock
    private SpecialtyRepository specialtyRepository;
    @Mock
    private LocationRepository locationRepository;

    private static AppointmentFact cardiologySi(AppointmentStatus status) {
        return new AppointmentFact(CARDIOLOGY, "Cardiología", SAN_ISIDRO, "Sede San Isidro", status);
    }

    private static AppointmentFact cardiologyLm(AppointmentStatus status) {
        return new AppointmentFact(CARDIOLOGY, "Cardiología", LA_MOLINA, "Sede La Molina", status);
    }

    private static AppointmentFact dermatologySi(AppointmentStatus status) {
        return new AppointmentFact(DERMATOLOGY, "Dermatología", SAN_ISIDRO, "Sede San Isidro", status);
    }

    private static SlotFact slot(long specialtyId, String specialtyName, long locationId, String locationName,
                                 SlotStatus status) {
        return new SlotFact(specialtyId, specialtyName, locationId, locationName, status);
    }

    /** Escenario común: 8 citas en dos especialidades y dos sedes. */
    private static List<AppointmentFact> scenario() {
        return List.of(
                cardiologySi(AppointmentStatus.BOOKED),
                cardiologySi(AppointmentStatus.ATTENDED),
                cardiologySi(AppointmentStatus.ATTENDED),
                cardiologySi(AppointmentStatus.NO_SHOW),
                cardiologySi(AppointmentStatus.CANCELLED),
                cardiologyLm(AppointmentStatus.ATTENDED),
                dermatologySi(AppointmentStatus.NO_SHOW),
                dermatologySi(AppointmentStatus.NO_SHOW));
    }

    @Nested
    @DisplayName("Citas por estado")
    class CitasPorEstado {

        @Test
        @DisplayName("Cuenta cada estado en total, por especialidad (orden alfabético) y por sede")
        void cuentaPorEstadoEspecialidadYSede() {
            AppointmentsReport report = ReportService.summarizeAppointments(FROM, TO, scenario());

            assertAll(
                    () -> assertThat(report.from()).isEqualTo(FROM),
                    () -> assertThat(report.totals()).isEqualTo(new StatusTotals(1, 3, 1, 3, 8)),
                    () -> assertThat(report.bySpecialty()).containsExactly(
                            new SpecialtyStatusRow(CARDIOLOGY, "Cardiología", new StatusTotals(1, 3, 1, 1, 6)),
                            new SpecialtyStatusRow(DERMATOLOGY, "Dermatología", new StatusTotals(0, 0, 0, 2, 2))),
                    () -> assertThat(report.byLocation()).extracting(row -> row.locationName())
                            .containsExactly("Sede La Molina", "Sede San Isidro"),
                    () -> assertThat(report.byLocation().get(0).totals()).isEqualTo(new StatusTotals(0, 1, 0, 0, 1)),
                    () -> assertThat(report.byLocation().get(1).totals()).isEqualTo(new StatusTotals(1, 2, 1, 3, 7)));
        }

        @Test
        @DisplayName("Sin citas devuelve totales en cero y listas vacías")
        void sinCitas() {
            AppointmentsReport report = ReportService.summarizeAppointments(FROM, TO, List.of());

            assertAll(
                    () -> assertThat(report.totals()).isEqualTo(StatusTotals.EMPTY),
                    () -> assertThat(report.bySpecialty()).isEmpty(),
                    () -> assertThat(report.byLocation()).isEmpty());
        }
    }

    @Nested
    @DisplayName("Tasa de inasistencia")
    class TasaDeInasistencia {

        @Test
        @DisplayName("Divide las inasistencias entre atendidas más inasistencias, ignorando reservadas y anuladas")
        void calculaLaTasaPorEspecialidad() {
            NoShowRateReport report = ReportService.summarizeNoShow(FROM, TO, scenario());

            assertAll(
                    () -> assertThat(report.bySpecialty()).hasSize(2),
                    () -> assertThat(report.bySpecialty().get(0).specialtyName()).isEqualTo("Cardiología"),
                    () -> assertThat(report.bySpecialty().get(0).attended()).isEqualTo(3),
                    () -> assertThat(report.bySpecialty().get(0).noShow()).isEqualTo(1),
                    () -> assertThat(report.bySpecialty().get(0).rate()).isEqualTo(0.25),
                    () -> assertThat(report.bySpecialty().get(1).rate()).isEqualTo(1.0),
                    () -> assertThat(report.total().attended()).isEqualTo(3),
                    () -> assertThat(report.total().noShow()).isEqualTo(3),
                    () -> assertThat(report.total().rate()).isEqualTo(0.5));
        }

        @Test
        @DisplayName("Una especialidad con solo citas reservadas o anuladas no aparece y la tasa general es 0")
        void sinAtencionesLaTasaEsCero() {
            NoShowRateReport report = ReportService.summarizeNoShow(FROM, TO,
                    List.of(cardiologySi(AppointmentStatus.BOOKED), dermatologySi(AppointmentStatus.CANCELLED)));

            assertAll(
                    () -> assertThat(report.bySpecialty()).isEmpty(),
                    () -> assertThat(report.total().rate()).isZero());
        }
    }

    @Nested
    @DisplayName("Ocupación")
    class Ocupacion {

        @Test
        @DisplayName("Cuenta citas reservadas, atendidas y no asistidas sobre los cupos sin bloquear, por especialidad y sede")
        void calculaLaOcupacion() {
            List<SlotFact> slots = List.of(
                    slot(CARDIOLOGY, "Cardiología", SAN_ISIDRO, "Sede San Isidro", SlotStatus.BOOKED),
                    slot(CARDIOLOGY, "Cardiología", SAN_ISIDRO, "Sede San Isidro", SlotStatus.BOOKED),
                    slot(CARDIOLOGY, "Cardiología", SAN_ISIDRO, "Sede San Isidro", SlotStatus.BOOKED),
                    slot(CARDIOLOGY, "Cardiología", SAN_ISIDRO, "Sede San Isidro", SlotStatus.BOOKED),
                    slot(CARDIOLOGY, "Cardiología", SAN_ISIDRO, "Sede San Isidro", SlotStatus.FREE),
                    slot(CARDIOLOGY, "Cardiología", SAN_ISIDRO, "Sede San Isidro", SlotStatus.BLOCKED),
                    slot(CARDIOLOGY, "Cardiología", LA_MOLINA, "Sede La Molina", SlotStatus.BOOKED),
                    slot(CARDIOLOGY, "Cardiología", LA_MOLINA, "Sede La Molina", SlotStatus.FREE),
                    slot(DERMATOLOGY, "Dermatología", SAN_ISIDRO, "Sede San Isidro", SlotStatus.BOOKED),
                    slot(DERMATOLOGY, "Dermatología", SAN_ISIDRO, "Sede San Isidro", SlotStatus.BOOKED));

            OccupancyReport report = ReportService.summarizeOccupancy(FROM, TO, slots, scenario());

            assertAll(
                    () -> assertThat(report.rows()).containsExactly(
                            new OccupancyRow(CARDIOLOGY, "Cardiología", LA_MOLINA, "Sede La Molina", 2, 0, 1, 0.5),
                            new OccupancyRow(CARDIOLOGY, "Cardiología", SAN_ISIDRO, "Sede San Isidro", 5, 1, 4, 0.8),
                            new OccupancyRow(DERMATOLOGY, "Dermatología", SAN_ISIDRO, "Sede San Isidro", 2, 0, 2, 1.0)),
                    () -> assertThat(report.total().slotsTotal()).isEqualTo(9),
                    () -> assertThat(report.total().slotsBlocked()).isEqualTo(1),
                    () -> assertThat(report.total().slotsBooked()).isEqualTo(7),
                    () -> assertThat(report.total().rate()).isEqualTo(0.7778));
        }

        @Test
        @DisplayName("Sin cupos la tasa es 0 aunque haya citas")
        void sinCuposLaTasaEsCero() {
            OccupancyReport report = ReportService.summarizeOccupancy(FROM, TO, List.of(),
                    List.of(cardiologySi(AppointmentStatus.ATTENDED)));

            assertAll(
                    () -> assertThat(report.rows()).hasSize(1),
                    () -> assertThat(report.rows().getFirst().slotsTotal()).isZero(),
                    () -> assertThat(report.rows().getFirst().slotsBooked()).isEqualTo(1),
                    () -> assertThat(report.rows().getFirst().rate()).isZero(),
                    () -> assertThat(report.total().rate()).isZero());
        }
    }

    @ParameterizedTest(name = "{0}/{1} = {2}")
    @CsvSource({
            "1, 4, 0.25",
            "2, 3, 0.6667",
            "1, 3, 0.3333",
            "0, 0, 0.0",
            "5, 0, 0.0"
    })
    @DisplayName("Las tasas se redondean a cuatro decimales y valen 0 sin denominador")
    void redondeaLasTasas(long numerator, long denominator, double expected) {
        assertThat(ReportService.rate(numerator, denominator)).isEqualTo(expected);
    }

    @Nested
    @DisplayName("Rango de fechas")
    class RangoDeFechas {

        @Test
        @DisplayName("Acepta hasta 366 días")
        void aceptaHasta366Dias() {
            ReportService.validateRange(FROM, FROM);
            ReportService.validateRange(FROM, FROM.plusDays(365));
        }

        @Test
        @DisplayName("Rechaza con 400 un rango invertido o de más de 366 días")
        void rechazaRangosInvalidos() {
            assertAll(
                    () -> assertThatThrownBy(() -> ReportService.validateRange(FROM, FROM.minusDays(1)))
                            .isInstanceOf(BusinessException.class)
                            .satisfies(ex -> assertThat(((BusinessException) ex).getStatus().value()).isEqualTo(400)),
                    () -> assertThatThrownBy(() -> ReportService.validateRange(FROM, FROM.plusDays(366)))
                            .isInstanceOf(BusinessException.class)
                            .hasMessageContaining("366 días"));
        }
    }

    @Nested
    @DisplayName("Exportación CSV")
    class ExportacionCsv {

        @Test
        @DisplayName("Resuelve el reporte por su nombre en la URL y rechaza los desconocidos")
        void resuelveElReporte() {
            assertAll(
                    () -> assertThat(ReportKind.fromParam("appointments")).isEqualTo(ReportKind.APPOINTMENTS),
                    () -> assertThat(ReportKind.fromParam("No-Show-Rate")).isEqualTo(ReportKind.NO_SHOW_RATE),
                    () -> assertThat(ReportKind.fromParam(" occupancy ")).isEqualTo(ReportKind.OCCUPANCY),
                    () -> assertThatThrownBy(() -> ReportKind.fromParam("ventas")).isInstanceOf(BusinessException.class),
                    () -> assertThatThrownBy(() -> ReportKind.fromParam(null)).isInstanceOf(BusinessException.class));
        }

        @Test
        @DisplayName("El CSV empieza con el BOM, separa con coma, termina cada línea con CRLF y entrecomilla las comas")
        void escribeElCsv() {
            AppointmentsReport report = ReportService.summarizeAppointments(FROM, TO, List.of(
                    new AppointmentFact(3L, "Ginecología, Obstetricia", SAN_ISIDRO, "Sede San Isidro",
                            AppointmentStatus.ATTENDED)));

            String csv = new String(ReportService.toCsv(report), StandardCharsets.UTF_8);

            assertAll(
                    () -> assertThat(csv).startsWith(CsvWriter.BOM + "grupo,nombre,reservadas,atendidas,anuladas,inasistencias,total\r\n"),
                    () -> assertThat(csv).contains("Especialidad,\"Ginecología, Obstetricia\",0,1,0,0,1\r\n"),
                    () -> assertThat(csv).endsWith("Total,Todas,0,1,0,0,1\r\n"));
        }

        @Test
        @DisplayName("exportCsv consulta las citas del rango y nombra el archivo con el reporte y las fechas")
        void exportaConNombreDescriptivo() {
            when(appointmentRepository.findWithDetailBySlotDateBetween(FROM, TO)).thenReturn(List.of());
            ReportService service = new ReportService(appointmentRepository, slotRepository, specialtyRepository,
                    locationRepository);

            CsvFile file = service.exportCsv("no-show-rate", FROM, TO);

            verify(appointmentRepository).findWithDetailBySlotDateBetween(FROM, TO);
            assertAll(
                    () -> assertThat(file.fileName()).isEqualTo("reporte-inasistencia_2026-10-01_2026-10-31.csv"),
                    () -> assertThat(new String(file.content(), StandardCharsets.UTF_8))
                            .isEqualTo(CsvWriter.BOM + "especialidad,atendidas,inasistencias,tasa_inasistencia\r\n"
                                    + "Total,0,0,0.0000\r\n"));
        }
    }
}
