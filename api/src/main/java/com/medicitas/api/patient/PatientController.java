package com.medicitas.api.patient;

import com.medicitas.api.patient.PatientDtos.InsuranceRequest;
import com.medicitas.api.patient.PatientDtos.PatientRequest;
import com.medicitas.api.patient.PatientDtos.PatientResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/patients")
@Tag(name = "Pacientes", description = "Identificación, registro y seguro del paciente")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @GetMapping
    @Operation(summary = "Identificar a un paciente por su documento (RF-02)")
    public PatientResponse findByDocument(@RequestParam DocumentType documentType,
                                          @RequestParam String documentNumber) {
        return patientService.findByDocument(documentType, documentNumber);
    }

    @PostMapping
    @Operation(summary = "Registrar un paciente nuevo (RF-01)")
    public ResponseEntity<PatientResponse> register(@Valid @RequestBody PatientRequest request) {
        PatientResponse patient = patientService.register(request);
        return ResponseEntity.created(URI.create("/api/v1/patients/" + patient.id())).body(patient);
    }

    @PutMapping("/{id}/insurance")
    @Operation(summary = "Asociar aseguradora y plan, o registrar atención particular (RF-03)")
    public PatientResponse updateInsurance(@PathVariable Long id, @Valid @RequestBody InsuranceRequest request) {
        return patientService.updateInsurance(id, request);
    }
}
