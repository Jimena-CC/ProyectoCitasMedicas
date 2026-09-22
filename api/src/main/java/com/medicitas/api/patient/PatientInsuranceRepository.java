package com.medicitas.api.patient;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PatientInsuranceRepository extends JpaRepository<PatientInsurance, Long> {

    List<PatientInsurance> findByPatientId(Long patientId);

    @Query("""
            select pi from PatientInsurance pi
            join fetch pi.plan p join fetch p.insurer
            where pi.patient.id = :patientId and pi.coverageStatus = :status
            order by pi.id desc""")
    List<PatientInsurance> findWithPlanByPatientAndStatus(@Param("patientId") Long patientId,
                                                          @Param("status") CoverageStatus status);
}
