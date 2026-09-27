package com.medicitas.api.patient;

import com.medicitas.api.catalog.InsurancePlan;
import com.medicitas.api.catalog.InsurancePlanRepository;
import com.medicitas.api.common.BusinessException;
import com.medicitas.api.common.ErrorCodes;
import com.medicitas.api.patient.PatientDtos.InsuranceRequest;
import com.medicitas.api.patient.PatientDtos.InsuranceResponse;
import com.medicitas.api.patient.PatientDtos.PatientRequest;
import com.medicitas.api.patient.PatientDtos.PatientResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class PatientService {

    private static final Logger logger = LoggerFactory.getLogger(PatientService.class);

    private static final Pattern DNI = Pattern.compile("\\d{8}");
    private static final Pattern FOREIGN_CARD = Pattern.compile("[A-Z0-9]{9,12}");
    private static final Pattern PASSPORT = Pattern.compile("[A-Z0-9]{6,12}");

    private static final String DUPLICATE_DOCUMENT_MESSAGE =
            "Ya existe un paciente registrado con ese documento. Identifícate con él para continuar.";

    private final PatientRepository patientRepository;
    private final PatientInsuranceRepository patientInsuranceRepository;
    private final InsurancePlanRepository insurancePlanRepository;
    private final Clock clock;

    public PatientService(PatientRepository patientRepository, PatientInsuranceRepository patientInsuranceRepository,
                          InsurancePlanRepository insurancePlanRepository, Clock clock) {
        this.patientRepository = patientRepository;
        this.patientInsuranceRepository = patientInsuranceRepository;
        this.insurancePlanRepository = insurancePlanRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PatientResponse findByDocument(DocumentType documentType, String documentNumber) {
        String number = normalizeDocument(documentNumber);
        return patientRepository.findByDocumentTypeAndDocumentNumber(documentType, number)
                .map(this::toResponse)
                .orElseThrow(() -> BusinessException.notFound(ErrorCodes.PATIENT_NOT_FOUND,
                        "No encontramos un paciente con ese documento. Regístrate para continuar."));
    }

    @Transactional
    public PatientResponse register(PatientRequest request) {
        String number = normalizeDocument(request.documentNumber());
        validateDocumentFormat(request.documentType(), number);
        if (patientRepository.existsByDocumentTypeAndDocumentNumber(request.documentType(), number)) {
            throw BusinessException.conflict(ErrorCodes.DUPLICATE_DOCUMENT, DUPLICATE_DOCUMENT_MESSAGE);
        }
        Patient patient = new Patient(request.documentType(), number, request.firstNames().trim(),
                request.lastNames().trim(), request.birthDate(), request.sex(), request.phone(),
                request.email().trim().toLowerCase(Locale.ROOT),
                request.acceptsNotifications() == null || request.acceptsNotifications(), Instant.now(clock));
        try {
            patientRepository.saveAndFlush(patient);
        } catch (DataIntegrityViolationException ex) {
            throw BusinessException.conflict(ErrorCodes.DUPLICATE_DOCUMENT, DUPLICATE_DOCUMENT_MESSAGE);
        }
        logger.info("Paciente registrado con id {}", patient.getId());
        return toResponse(patient);
    }

    /**
     * Asocia el plan elegido como cobertura vigente. Los demás planes del paciente quedan suspendidos;
     * sin {@code planId} el paciente pasa a atención particular.
     */
    @Transactional
    public PatientResponse updateInsurance(Long patientId, InsuranceRequest request) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(PatientService::patientNotFound);
        List<PatientInsurance> insurances = patientInsuranceRepository.findByPatientId(patientId);

        PatientInsurance chosen = null;
        if (request.planId() != null) {
            InsurancePlan plan = insurancePlanRepository.findById(request.planId())
                    .filter(InsurancePlan::isActive)
                    .orElseThrow(() -> BusinessException.notFound(ErrorCodes.RESOURCE_NOT_FOUND,
                            "El plan de seguro seleccionado no existe."));
            chosen = insurances.stream()
                    .filter(insurance -> insurance.getPlan().getId().equals(plan.getId()))
                    .findFirst()
                    .orElseGet(() -> patientInsuranceRepository.save(
                            new PatientInsurance(patient, plan, null, CoverageStatus.ACTIVE, null)));
            chosen.setPolicyNumber(optionalText(request.policyNumber()));
            chosen.setCoverageStatus(CoverageStatus.ACTIVE);
        }
        for (PatientInsurance insurance : insurances) {
            if (insurance != chosen && insurance.getCoverageStatus() == CoverageStatus.ACTIVE) {
                insurance.setCoverageStatus(CoverageStatus.SUSPENDED);
            }
        }
        logger.info("Cobertura del paciente {} actualizada a {}", patientId,
                chosen == null ? "particular" : "plan " + request.planId());
        return toResponse(patient);
    }

    private PatientResponse toResponse(Patient patient) {
        InsuranceResponse insurance = patientInsuranceRepository
                .findWithPlanByPatientAndStatus(patient.getId(), CoverageStatus.ACTIVE).stream()
                .findFirst()
                .map(pi -> new InsuranceResponse(pi.getId(), pi.getPlan().getInsurer().getName(),
                        pi.getPlan().getName(), pi.getPolicyNumber(), pi.getCoverageStatus()))
                .orElse(null);
        return new PatientResponse(patient.getId(), patient.getDocumentType(), patient.getDocumentNumber(),
                patient.getFirstNames(), patient.getLastNames(), patient.getBirthDate(),
                patient.getPhone(), patient.getEmail(), insurance);
    }

    private static void validateDocumentFormat(DocumentType type, String number) {
        boolean valid = switch (type) {
            case DNI -> DNI.matcher(number).matches();
            case CE -> FOREIGN_CARD.matcher(number).matches();
            case PAS -> PASSPORT.matcher(number).matches();
        };
        if (!valid) {
            String message = switch (type) {
                case DNI -> "El DNI debe tener exactamente 8 dígitos.";
                case CE -> "El carné de extranjería debe tener entre 9 y 12 caracteres.";
                case PAS -> "El pasaporte debe tener entre 6 y 12 caracteres.";
            };
            throw BusinessException.invalidData("documentNumber", message);
        }
    }

    private static String normalizeDocument(String documentNumber) {
        return documentNumber == null ? "" : documentNumber.trim().toUpperCase(Locale.ROOT);
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static BusinessException patientNotFound() {
        return BusinessException.notFound(ErrorCodes.PATIENT_NOT_FOUND,
                "No encontramos al paciente. Vuelve a identificarte con tu documento.");
    }
}
