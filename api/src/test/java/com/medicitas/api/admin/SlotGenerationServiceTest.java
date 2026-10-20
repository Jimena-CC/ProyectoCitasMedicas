package com.medicitas.api.admin;

import com.medicitas.api.admin.AdminSlotDtos.SlotGenerationRequest;
import com.medicitas.api.admin.AdminSlotDtos.SlotGenerationResult;
import com.medicitas.api.admin.SlotGenerationService.Plan;
import com.medicitas.api.admin.SlotGenerationService.TimeWindow;
import com.medicitas.api.audit.AuditAction;
import com.medicitas.api.audit.AuditService;
import com.medicitas.api.catalog.Doctor;
import com.medicitas.api.catalog.DoctorRepository;
import com.medicitas.api.catalog.Location;
import com.medicitas.api.catalog.LocationRepository;
import com.medicitas.api.catalog.LocationSpecialtyRepository;
import com.medicitas.api.catalog.Specialty;
import com.medicitas.api.common.BusinessException;
import com.medicitas.api.common.ErrorCodes;
import com.medicitas.api.schedule.Slot;
import com.medicitas.api.schedule.SlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cálculo puro de horarios de {@link SlotGenerationService#plan} y reglas de negocio de la generación.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SlotGenerationService")
class SlotGenerationServiceTest {

    /** Lunes 2026-10-19 a las 09:00 en Lima. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T14:00:00Z"), ZoneId.of("America/Lima"));
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 25);
    private static final LocalTime EIGHT = LocalTime.of(8, 0);
    private static final LocalTime TEN = LocalTime.of(10, 0);
    private static final Set<DayOfWeek> EVERY_DAY = EnumSet.allOf(DayOfWeek.class);

    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private LocationSpecialtyRepository locationSpecialtyRepository;
    @Mock
    private SlotRepository slotRepository;
    @Mock
    private AuditService auditService;

    @Captor
    private ArgumentCaptor<List<Slot>> savedSlots;

    private SlotGenerationService service;
    private Doctor doctor;
    private Location location;

    @BeforeEach
    void setUp() {
        service = new SlotGenerationService(doctorRepository, locationRepository, locationSpecialtyRepository,
                slotRepository, auditService, CLOCK);
        doctor = new Doctor("012345", null, "Carla", "Benavides Ortiz", new Specialty("Cardiología", null, (short) 20));
        location = new Location("SI", "Sede San Isidro", "Av. Alfredo Salazar 350", "San Isidro", null);
    }

    private static SlotGenerationRequest request(LocalDate from, LocalDate to, LocalTime start, LocalTime end,
                                                 Set<DayOfWeek> weekdays, Integer duration) {
        return new SlotGenerationRequest(1L, 2L, "Consultorio 201", from, to, start, end, weekdays, duration);
    }

    @Nested
    @DisplayName("Cálculo de horarios")
    class CalculoDeHorarios {

        @Test
        @DisplayName("Divide el horario de cada día en bloques consecutivos de la duración indicada")
        void divideElHorarioEnBloques() {
            Plan plan = SlotGenerationService.plan(MONDAY, MONDAY, EIGHT, TEN, EVERY_DAY, 20, List.of());

            assertAll(
                    () -> assertThat(plan.skipped()).isZero(),
                    () -> assertThat(plan.toCreate()).hasSize(6),
                    () -> assertThat(plan.toCreate().getFirst())
                            .isEqualTo(new TimeWindow(MONDAY, EIGHT, LocalTime.of(8, 20))),
                    () -> assertThat(plan.toCreate().getLast())
                            .isEqualTo(new TimeWindow(MONDAY, LocalTime.of(9, 40), TEN)));
        }

        @ParameterizedTest(name = "de 08:00 a {0} con {1} min caben {2} cupos")
        @CsvSource({
                "09:50, 20, 5",
                "10:00, 30, 4",
                "08:45, 30, 1",
                "08:15, 20, 0"
        })
        @DisplayName("El último cupo nunca termina después de la hora de fin")
        void elUltimoCupoNoPasaLaHoraDeFin(String end, int duration, int expected) {
            Plan plan = SlotGenerationService.plan(MONDAY, MONDAY, EIGHT, LocalTime.parse(end), EVERY_DAY, duration,
                    List.of());

            assertThat(plan.toCreate()).hasSize(expected);
        }

        @Test
        @DisplayName("Solo genera los días de la semana indicados: de lunes a sábado omite el domingo")
        void respetaLosDiasDeLaSemana() {
            Plan plan = SlotGenerationService.plan(MONDAY, SUNDAY, EIGHT, LocalTime.of(8, 20),
                    SlotGenerationService.DEFAULT_WEEKDAYS, 20, List.of());

            assertAll(
                    () -> assertThat(plan.toCreate()).hasSize(6),
                    () -> assertThat(plan.toCreate()).extracting(TimeWindow::date).doesNotContain(SUNDAY),
                    () -> assertThat(SlotGenerationService.plan(MONDAY, SUNDAY, EIGHT, LocalTime.of(8, 20),
                            EnumSet.of(DayOfWeek.SUNDAY), 20, List.of()).toCreate())
                            .extracting(TimeWindow::date).containsExactly(SUNDAY));
        }

        @Test
        @DisplayName("Omite los horarios que coinciden o se cruzan con cupos existentes del médico")
        void omiteLosHorariosQueSeCruzan() {
            List<TimeWindow> existing = List.of(
                    new TimeWindow(MONDAY, EIGHT, LocalTime.of(8, 20)),            // idéntico al primero
                    new TimeWindow(MONDAY, LocalTime.of(9, 10), LocalTime.of(9, 30)), // cruza 09:00-09:20 y 09:20-09:40
                    new TimeWindow(MONDAY.plusDays(1), EIGHT, TEN));                // otro día: no afecta

            Plan plan = SlotGenerationService.plan(MONDAY, MONDAY, EIGHT, TEN, EVERY_DAY, 20, existing);

            assertAll(
                    () -> assertThat(plan.skipped()).isEqualTo(3),
                    () -> assertThat(plan.toCreate()).extracting(TimeWindow::startTime)
                            .containsExactly(LocalTime.of(8, 20), LocalTime.of(8, 40), LocalTime.of(9, 40)));
        }

        @Test
        @DisplayName("Un cupo que termina justo cuando empieza otro no se cruza con él")
        void cuposContiguosNoSeCruzan() {
            TimeWindow first = new TimeWindow(MONDAY, EIGHT, LocalTime.of(8, 20));
            TimeWindow second = new TimeWindow(MONDAY, LocalTime.of(8, 20), LocalTime.of(8, 40));

            assertAll(
                    () -> assertThat(first.overlaps(second)).isFalse(),
                    () -> assertThat(first.overlaps(first)).isTrue());
        }
    }

    @Nested
    @DisplayName("Reglas de la generación")
    class ReglasDeLaGeneracion {

        private void givenActiveDoctorAtOfferedLocation() {
            when(doctorRepository.findWithSpecialtyById(1L)).thenReturn(Optional.of(doctor));
            when(locationRepository.findById(2L)).thenReturn(Optional.of(location));
            when(locationSpecialtyRepository.isOffered(any(), any())).thenReturn(true);
        }

        @Test
        @DisplayName("Guarda los cupos libres con la duración de la especialidad y audita la generación")
        void guardaLosCuposYAudita() {
            givenActiveDoctorAtOfferedLocation();
            when(slotRepository.findByDoctorAndRange(any(), eq(MONDAY), eq(MONDAY.plusDays(1))))
                    .thenReturn(List.of(new Slot(doctor, location, MONDAY, EIGHT, LocalTime.of(8, 20), null)));

            SlotGenerationResult result = service.generate(
                    request(MONDAY, MONDAY.plusDays(1), EIGHT, TEN, null, null));

            verify(slotRepository).saveAll(savedSlots.capture());
            assertAll(
                    () -> assertThat(result.created()).isEqualTo(11),
                    () -> assertThat(result.skipped()).isEqualTo(1),
                    () -> assertThat(result.slots()).hasSize(11),
                    () -> assertThat(savedSlots.getValue()).hasSize(11)
                            .allSatisfy(slot -> assertThat(slot.getRoom()).isEqualTo("Consultorio 201")),
                    () -> assertThat(savedSlots.getValue().getFirst().getStartTime()).isEqualTo(LocalTime.of(8, 20)),
                    () -> assertThat(savedSlots.getValue().getFirst().getEndTime()).isEqualTo(LocalTime.of(8, 40)));
            verify(auditService).record(eq(AuditAction.SLOTS_GENERATED), isNull(), contains("11 cupos de 20 min"));
        }

        @Test
        @DisplayName("Un médico inactivo responde 422 DOCTOR_INACTIVE sin tocar la agenda")
        void medicoInactivo() {
            doctor.setActive(false);
            when(doctorRepository.findWithSpecialtyById(1L)).thenReturn(Optional.of(doctor));

            assertThatThrownBy(() -> service.generate(request(MONDAY, MONDAY, EIGHT, TEN, null, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getCode()).isEqualTo(ErrorCodes.DOCTOR_INACTIVE);
            verify(slotRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("Una sede que no atiende la especialidad responde 422 SPECIALTY_NOT_OFFERED")
        void sedeQueNoAtiendeLaEspecialidad() {
            when(doctorRepository.findWithSpecialtyById(1L)).thenReturn(Optional.of(doctor));
            when(locationRepository.findById(2L)).thenReturn(Optional.of(location));
            when(locationSpecialtyRepository.isOffered(any(), any())).thenReturn(false);

            assertThatThrownBy(() -> service.generate(request(MONDAY, MONDAY, EIGHT, TEN, null, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getCode()).isEqualTo(ErrorCodes.SPECIALTY_NOT_OFFERED);
        }

        @Test
        @DisplayName("Una fecha inicial anterior a hoy, un rango invertido o un horario sin espacio responden 400")
        void rangosInvalidos() {
            givenActiveDoctorAtOfferedLocation();

            assertAll(
                    () -> assertThatThrownBy(() -> service.generate(
                            request(MONDAY.minusDays(1), MONDAY, EIGHT, TEN, null, null)))
                            .isInstanceOf(BusinessException.class).hasMessageContaining("anterior a hoy"),
                    () -> assertThatThrownBy(() -> service.generate(
                            request(MONDAY.plusDays(1), MONDAY, EIGHT, TEN, null, null)))
                            .isInstanceOf(BusinessException.class).hasMessageContaining("fecha final"),
                    () -> assertThatThrownBy(() -> service.generate(
                            request(MONDAY, MONDAY.plusDays(SlotGenerationService.MAX_RANGE_DAYS), EIGHT, TEN, null, null)))
                            .isInstanceOf(BusinessException.class).hasMessageContaining("92 días"),
                    () -> assertThatThrownBy(() -> service.generate(
                            request(MONDAY, MONDAY, TEN, EIGHT, null, null)))
                            .isInstanceOf(BusinessException.class).hasMessageContaining("hora de fin"),
                    () -> assertThatThrownBy(() -> service.generate(
                            request(MONDAY, MONDAY, EIGHT, LocalTime.of(8, 10), null, 30)))
                            .isInstanceOf(BusinessException.class).hasMessageContaining("no cabe"));
            verify(slotRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("Un médico o una sede inexistentes responden 404")
        void medicoOSedeInexistentes() {
            when(doctorRepository.findWithSpecialtyById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generate(request(MONDAY, MONDAY, EIGHT, TEN, null, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getStatus().value()).isEqualTo(404);
        }
    }
}
