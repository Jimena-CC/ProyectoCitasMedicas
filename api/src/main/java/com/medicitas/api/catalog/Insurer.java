package com.medicitas.api.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "insurer")
public class Insurer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    /** RUC: identificador tributario peruano de la aseguradora. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ruc", nullable = false, unique = true, length = 11)
    private String ruc;

    @Column(name = "type", nullable = false, length = 15)
    private String type;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "insurer")
    @OrderBy("name ASC")
    private List<InsurancePlan> plans = new ArrayList<>();

    protected Insurer() {
    }

    public Insurer(String name, String ruc, String type) {
        this.name = name;
        this.ruc = ruc;
        this.type = type;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRuc() {
        return ruc;
    }

    public String getType() {
        return type;
    }

    public boolean isActive() {
        return active;
    }

    public List<InsurancePlan> getPlans() {
        return plans;
    }
}
