package com.medicitas.api.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

@Entity
@Table(name = "insurance_plan",
        uniqueConstraints = @UniqueConstraint(name = "uk_plan_insurer_name", columnNames = {"insurer_id", "name"}))
public class InsurancePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "insurer_id", nullable = false)
    private Insurer insurer;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "consultation_copay", precision = 8, scale = 2)
    private BigDecimal consultationCopay;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected InsurancePlan() {
    }

    public InsurancePlan(Insurer insurer, String name, BigDecimal consultationCopay) {
        this.insurer = insurer;
        this.name = name;
        this.consultationCopay = consultationCopay;
    }

    public Long getId() {
        return id;
    }

    public Insurer getInsurer() {
        return insurer;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getConsultationCopay() {
        return consultationCopay;
    }

    public boolean isActive() {
        return active;
    }
}
