package com.medicitas.api.frontdesk;

import com.medicitas.api.appointment.AppointmentDtos.AppointmentRequest;
import com.medicitas.api.appointment.AppointmentDtos.BookingResult;
import com.medicitas.api.appointment.AppointmentDtos.RescheduleRequest;
import com.medicitas.api.appointment.AppointmentService;
import com.medicitas.api.catalog.InsurancePlan;
import com.medicitas.api.catalog.Insurer;
import com.medicitas.api.common.BusinessException;
import com.medicitas.api.common.ErrorCodes;
import com.medicitas.api.frontdesk.FrontDeskDtos.CoverageValidation;
import com.medicitas.api.frontdesk.FrontDeskDtos.InsuranceValidationResponse;
import com.medicitas.api.patient.CoverageStatus;
import com.medicitas.api.patient.DocumentType;
import com.medicitas.api.patient.Patient;
import com.medicitas.api.patient.PatientDtos.InsuranceRequest;
import com.medicitas.api.patient.PatientInsurance;
import com.medicitas.api.patient.PatientInsuranceRepository;
import com.medicitas.api.patient.PatientRepository;
import com.medicitas.api.patient.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Validación de aseguradora en ventanilla y delegación de las demás operaciones en los servicios del kiosko.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FrontDeskService")
class FrontDeskServiceTest {

    /** Lunes 14 de setiembre de 2026, 10:00 en Lima. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-14T15:00:00Z"), ZoneId.of("America/Lima"));
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 14);

    @Mock
    private PatientService patientService;
    @Mock
    private AppointmentService appointmentService;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private PatientInsuranceRepository patientInsuranceRepository;

    private FrontDeskService service;
    private Patient patient;

    @BeforeEach
    void preparar() {
        service = new FrontDeskService(patientService, appointmentService, patientRepository,
                patientInsuranceRepository, CLOCK);
        patient = new Patient(DocumentType.DNI, "45871236", "Lucía", "Paredes Quispe", LocalDate.of(1991, 4, 18),
                "F", "987654321", "lucia.paredes@correo.pe", true, CLOCK.instant());
        ReflectionTestUtils.setField(patient, "id", 1L);
    }

    @Nested
    @DisplayName("Validación de aseguradora")
    class ValidacionDeAseguradora {

        @Test
        @DisplayName("Un paciente inexistente responde 404 PATIENT_NOT_FOUND")
        void pacienteInexistenteResponde404() {
            when(patientRepository.existsById(99L)).thenReturn(false);

            assertThatThrownBy(() -> service.validateInsurance(99L))
                    .isInstanceOfSatisfying(BusinessException.class, ex -> {
                        assertThat(ex.getCode()).isEqualTo(ErrorCodes.PATIENT_NOT_FOUND);
                        assertThat(ex.getStatus().value()).isEqualTo(404);
                    });
        }

        @Test
        @DisplayName("Sin seguros registrados indica que puede atenderse como particular")
        void sinSegurosIndicaParticular() {
            when(patientRepository.existsById(1L)).thenReturn(true);
            when(patientInsuranceRepository.findByPatientId(1L)).thenReturn(List.of());

            InsuranceValidationResponse result = service.validateInsurance(1L);

            assertAll(
                    () -> assertThat(result.patientId()).isEqualTo(1L),
                    () -> assertThat(result.valid()).isFalse(),
                    () -> assertThat(result.coverages()).isEmpty(),
                    () -> assertThat(result.message()).contains("particular"));
        }

        @ParameterizedTest(name = "{0} con vencimiento {1} -> valid={2}")
        @CsvSource({
                "ACTIVE,    ,           true",
                "ACTIVE,    2026-09-14, true",
                "ACTIVE,    2026-12-31, true",
                "ACTIVE,    2026-09-13, false",
                "SUSPENDED, ,           false",
                "SUSPENDED, 2026-12-31, false",
                "EXPIRED,   ,           false"
        })
        @DisplayName("Una cobertura es válida solo si está ACTIVE y vigente a la fecha de hoy")
        void coberturaValidaSoloSiActivaYVigente(CoverageStatus status, LocalDate validUntil, boolean expectedValid) {
            when(patientRepository.existsById(1L)).thenReturn(true);
            when(patientInsuranceRepository.findByPatientId(1L))
                    .thenReturn(List.of(insurance(5L, "Rímac Seguros", "Red Médica", status, validUntil)));

            InsuranceValidationResponse result = service.validateInsurance(1L);

            CoverageValidation coverage = result.coverages().getFirst();
            assertAll(
                    () -> assertThat(result.valid()).isEqualTo(expectedValid),
                    () -> assertThat(coverage.valid()).isEqualTo(expectedValid),
                    () -> assertThat(coverage.patientInsuranceId()).isEqualTo(5L),
                    () -> assertThat(coverage.insurer()).isEqualTo("Rímac Seguros"),
                    () -> assertThat(coverage.plan()).isEqualTo("Red Médica"),
                    () -> assertThat(coverage.coverageStatus()).isEqualTo(status),
                    () -> assertThat(coverage.validUntil()).isEqualTo(validUntil),
                    () -> assertThat(result.message()).contains(expectedValid ? "vigente" : "particular"));
        }

        @Test
        @DisplayName("Con varias coberturas, las vigentes van primero y el resultado global es válido")
        void variasCoberturasOrdenaVigentesPrimero() {
            when(patientRepository.existsById(1L)).thenReturn(true);
            when(patientInsuranceRepository.findByPatientId(1L)).thenReturn(List.of(
                    insurance(1L, "Pacífico Seguros", "Plan Esencial", CoverageStatus.SUSPENDED, null),
                    insurance(2L, "Rímac Seguros", "Red Médica", CoverageStatus.ACTIVE, TODAY.minusDays(1)),
                    insurance(3L, "Mapfre", "Plan Integral", CoverageStatus.ACTIVE, null)));

            InsuranceValidationResponse result = service.validateInsurance(1L);

            assertThat(result.valid()).isTrue();
            assertThat(result.coverages()).extracting(CoverageValidation::patientInsuranceId).containsExactly(3L, 2L, 1L);
            assertThat(result.coverages()).extracting(CoverageValidation::valid).containsExactly(true, false, false);
        }
    }

    @Nested
    @DisplayName("Delegación en los servicios del kiosko")
    class Delegacion {

        @Test
        @DisplayName("La búsqueda y el seguro del paciente se delegan en PatientService")
        void pacienteYSeguroSeDeleganEnPatientService() {
            InsuranceRequest insurance = new InsuranceRequest(2L, "RM-1");

            service.searchPatient(DocumentType.DNI, "45871236");
            service.updateInsurance(1L, insurance);

            verify(patientService).findByDocument(DocumentType.DNI, "45871236");
            verify(patientService).updateInsurance(1L, insurance);
        }

        @Test
        @DisplayName("Reservar, reprogramar y anular se delegan en AppointmentService con los mismos datos")
        void citasSeDeleganEnAppointmentService() {
            AppointmentRequest request = new AppointmentRequest(1L, 10L, null, "Control");
            UUID key = UUID.randomUUID();
            RescheduleRequest reschedule = new RescheduleRequest(11L);
            when(appointmentService.book(request, key)).thenReturn(new BookingResult(null, true));

            BookingResult result = service.book(request, key);
            service.reschedule(5L, reschedule);
            service.cancel(5L, "Viaje fuera de Lima");
            service.listAppointments(1L, false);

            assertThat(result.created()).isTrue();
            verify(appointmentService).reschedule(5L, reschedule);
            verify(appointmentService).cancel(5L, "Viaje fuera de Lima");
            verify(appointmentService).listByPatient(1L, false);
        }
    }

    private PatientInsurance insurance(long id, String insurerName, String planName, CoverageStatus status,
                                       LocalDate validUntil) {
        Insurer insurer = new Insurer(insurerName, "20999999001", "SEGURO_PRIVADO");
        InsurancePlan plan = new InsurancePlan(insurer, planName, new BigDecimal("45.00"));
        ReflectionTestUtils.setField(plan, "id", id);
        PatientInsurance insurance = new PatientInsurance(patient, plan, "POL-" + id, status, validUntil);
        ReflectionTestUtils.setField(insurance, "id", id);
        return insurance;
    }
}
