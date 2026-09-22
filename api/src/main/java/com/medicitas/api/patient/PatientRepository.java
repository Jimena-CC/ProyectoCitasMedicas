package com.medicitas.api.patient;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByDocumentTypeAndDocumentNumber(DocumentType documentType, String documentNumber);

    boolean existsByDocumentTypeAndDocumentNumber(DocumentType documentType, String documentNumber);
}
