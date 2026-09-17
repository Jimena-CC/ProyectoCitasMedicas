package com.medicitas.api.patient;

import com.medicitas.api.catalog.InsurancePlan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

/**
 * Plan de seguro asociado al paciente (RF-03). Una cita sin seguro es atención particular.
 */
@Entity
@Table(name = "patient_insurance",
        uniqueConstraints = @UniqueConstraint(name = "uk_patient_insurance_plan", columnNames = {"patient_id", "plan_id"}))
public class PatientInsurance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private InsurancePlan plan;

    @Column(name = "policy_number", length = 30)
    private String policyNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "coverage_status", nullable = false, length = 12)
    private CoverageStatus coverageStatus;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    protected PatientInsurance() {
    }

    public PatientInsurance(Patient patient, InsurancePlan plan, String policyNumber,
                            CoverageStatus coverageStatus, LocalDate validUntil) {
        this.patient = patient;
        this.plan = plan;
        this.policyNumber = policyNumber;
        this.coverageStatus = coverageStatus;
        this.validUntil = validUntil;
    }

    public boolean isActiveOn(LocalDate today) {
        return coverageStatus == CoverageStatus.ACTIVE && (validUntil == null || !validUntil.isBefore(today));
    }

    public String getCoverageDescription() {
        return plan.getInsurer().getName() + " · " + plan.getName();
    }

    public Long getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    public InsurancePlan getPlan() {
        return plan;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public void setPolicyNumber(String policyNumber) {
        this.policyNumber = policyNumber;
    }

    public CoverageStatus getCoverageStatus() {
        return coverageStatus;
    }

    public void setCoverageStatus(CoverageStatus coverageStatus) {
        this.coverageStatus = coverageStatus;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }
}
