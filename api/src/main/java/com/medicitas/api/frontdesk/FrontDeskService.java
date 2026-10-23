package com.medicitas.api.frontdesk;

import com.medicitas.api.appointment.AppointmentDtos.AppointmentRequest;
import com.medicitas.api.appointment.AppointmentDtos.AppointmentResponse;
import com.medicitas.api.appointment.AppointmentDtos.BookingResult;
import com.medicitas.api.appointment.AppointmentDtos.RescheduleRequest;
import com.medicitas.api.appointment.AppointmentService;
import com.medicitas.api.common.BusinessException;
import com.medicitas.api.common.ErrorCodes;
import com.medicitas.api.frontdesk.FrontDeskDtos.CoverageValidation;
import com.medicitas.api.frontdesk.FrontDeskDtos.InsuranceValidationResponse;
import com.medicitas.api.patient.DocumentType;
import com.medicitas.api.patient.PatientDtos.InsuranceRequest;
import com.medicitas.api.patient.PatientDtos.PatientRequest;
import com.medicitas.api.patient.PatientDtos.PatientResponse;
import com.medicitas.api.patient.PatientInsurance;
import com.medicitas.api.patient.PatientInsuranceRepository;
import com.medicitas.api.patient.PatientRepository;
import com.medicitas.api.patient.PatientService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Operaciones de ventanilla (CU-013 a CU-017; RF-22 a RF-26). Reutiliza las reglas del kiosko:
 * {@link PatientService} para pacientes y seguros y {@link AppointmentService} para citas, que ya atribuye
 * cada operación al usuario autenticado en el historial y la auditoría y reserva con canal {@code FRONT_DESK}.
 */
@Service
public class FrontDeskService {

    private static final String VALID_MESSAGE = "El paciente tiene una cobertura vigente. Puedes reservar con ese seguro.";
    private static final String SELF_PAY_MESSAGE =
            "El paciente no tiene una cobertura vigente. Puede atenderse como particular o actualizar su seguro.";
    private static final String NO_INSURANCE_MESSAGE =
            "El paciente no tiene ningún seguro registrado. Puede atenderse como particular o asociar un seguro.";

    private final PatientService patientService;
    private final AppointmentService appointmentService;
    private final PatientRepository patientRepository;
    private final PatientInsuranceRepository patientInsuranceRepository;
    private final Clock clock;

    public FrontDeskService(PatientService patientService, AppointmentService appointmentService,
                            PatientRepository patientRepository, PatientInsuranceRepository patientInsuranceRepository,
                            Clock clock) {
        this.patientService = patientService;
        this.appointmentService = appointmentService;
        this.patientRepository = patientRepository;
        this.patientInsuranceRepository = patientInsuranceRepository;
        this.clock = clock;
    }

    /**
     * Busca un paciente por documento (CU-013).
     *
     * @param documentType   tipo de documento
     * @param documentNumber número de documento
     * @return el paciente con su cobertura vigente
     * @throws BusinessException {@code 404 PATIENT_NOT_FOUND} si no existe
     */
    @Transactional(readOnly = true)
    public PatientResponse searchPatient(DocumentType documentType, String documentNumber) {
        return patientService.findByDocument(documentType, documentNumber);
    }

    /**
     * Registra un paciente con las mismas validaciones del kiosko (RF-01).
     *
     * @param request datos del paciente
     * @return el paciente creado
     */
    @Transactional
    public PatientResponse registerPatient(PatientRequest request) {
        return patientService.register(request);
    }

    /**
     * Valida la aseguradora del paciente (CU-014): lista todas sus coberturas e indica cuáles están activas y
     * vigentes a la fecha de hoy. Si ninguna lo está, el mensaje indica que puede atenderse como particular.
     *
     * @param patientId paciente
     * @return el resultado de la validación
     * @throws BusinessException {@code 404 PATIENT_NOT_FOUND} si el paciente no existe
     */
    @Transactional(readOnly = true)
    public InsuranceValidationResponse validateInsurance(Long patientId) {
        if (!patientRepository.existsById(patientId)) {
            throw BusinessException.notFound(ErrorCodes.PATIENT_NOT_FOUND,
                    "No encontramos al paciente. Búscalo nuevamente por su documento.");
        }
        LocalDate today = LocalDate.now(clock);
        List<CoverageValidation> coverages = patientInsuranceRepository.findByPatientId(patientId).stream()
                .map(insurance -> CoverageValidation.of(insurance, today))
                .sorted(Comparator.comparing(CoverageValidation::valid).reversed()
                        .thenComparing(CoverageValidation::patientInsuranceId, Comparator.reverseOrder()))
                .toList();
        boolean valid = coverages.stream().anyMatch(CoverageValidation::valid);
        String message = valid ? VALID_MESSAGE : coverages.isEmpty() ? NO_INSURANCE_MESSAGE : SELF_PAY_MESSAGE;
        return new InsuranceValidationResponse(patientId, valid, message, coverages);
    }

    /**
     * Asocia o actualiza el seguro del paciente, o lo deja como particular (RF-03).
     *
     * @param patientId paciente
     * @param request   plan elegido y número de póliza
     * @return el paciente con su cobertura vigente
     */
    @Transactional
    public PatientResponse updateInsurance(Long patientId, InsuranceRequest request) {
        return patientService.updateInsurance(patientId, request);
    }

    /**
     * Citas del paciente.
     *
     * @param patientId    paciente
     * @param onlyUpcoming {@code true} para solo las reservadas y futuras
     * @return las citas
     */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> listAppointments(Long patientId, boolean onlyUpcoming) {
        return appointmentService.listByPatient(patientId, onlyUpcoming);
    }

    /**
     * Reserva desde ventanilla (CU-015). Como hay usuario autenticado, {@link AppointmentService} registra
     * el canal {@code FRONT_DESK} y atribuye la operación al usuario; aplica RF-07 y RF-08.
     *
     * @param request        datos de la reserva
     * @param idempotencyKey clave del intento
     * @return la cita y si fue creada en esta llamada
     */
    @Transactional
    public BookingResult book(AppointmentRequest request, UUID idempotencyKey) {
        return appointmentService.book(request, idempotencyKey);
    }

    /**
     * Reprograma una cita desde ventanilla (CU-016).
     *
     * @param appointmentId cita
     * @param request       nuevo cupo
     * @return la cita actualizada
     */
    @Transactional
    public AppointmentResponse reschedule(Long appointmentId, RescheduleRequest request) {
        return appointmentService.reschedule(appointmentId, request);
    }

    /**
     * Anula una cita desde ventanilla con motivo obligatorio (CU-016).
     *
     * @param appointmentId cita
     * @param reason        motivo de la anulación
     * @return la cita anulada
     */
    @Transactional
    public AppointmentResponse cancel(Long appointmentId, String reason) {
        return appointmentService.cancel(appointmentId, reason);
    }
}
