package com.medicitas.api.patient;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "patient",
        uniqueConstraints = @UniqueConstraint(name = "uk_patient_document", columnNames = {"document_type", "document_number"}))
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 3)
    private DocumentType documentType;

    @Column(name = "document_number", nullable = false, length = 12)
    private String documentNumber;

    @Column(name = "first_names", nullable = false, length = 100)
    private String firstNames;

    @Column(name = "last_names", nullable = false, length = 100)
    private String lastNames;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sex", length = 1)
    private String sex;

    @Column(name = "phone", nullable = false, length = 15)
    private String phone;

    @Column(name = "email", nullable = false, length = 120)
    private String email;

    @Column(name = "accepts_notifications", nullable = false)
    private boolean acceptsNotifications = true;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    protected Patient() {
    }

    public Patient(DocumentType documentType, String documentNumber, String firstNames, String lastNames,
                   LocalDate birthDate, String sex, String phone, String email,
                   boolean acceptsNotifications, Instant registeredAt) {
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.firstNames = firstNames;
        this.lastNames = lastNames;
        this.birthDate = birthDate;
        this.sex = sex;
        this.phone = phone;
        this.email = email;
        this.acceptsNotifications = acceptsNotifications;
        this.registeredAt = registeredAt;
    }

    public String getFullName() {
        return firstNames + " " + lastNames;
    }

    public Long getId() {
        return id;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public String getFirstNames() {
        return firstNames;
    }

    public String getLastNames() {
        return lastNames;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getSex() {
        return sex;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public boolean isAcceptsNotifications() {
        return acceptsNotifications;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }
}
