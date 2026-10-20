package com.medicitas.api.audit;

import com.medicitas.api.security.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Registro de auditoría (tabla {@code audit_log}, Ley 29733): quién hizo qué, sobre qué y desde dónde.
 * {@code user} es nulo cuando la operación la hizo el paciente desde el kiosko.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(name = "action", nullable = false, length = 60)
    private String action;

    @Column(name = "entity", nullable = false, length = 40)
    private String entity;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    protected AuditLog() {
    }

    /**
     * Crea un registro de auditoría.
     *
     * @param user         usuario responsable o {@code null} si actuó el paciente desde el kiosko
     * @param action       acción realizada, por ejemplo {@code APPOINTMENT_BOOKED}
     * @param entity       entidad afectada, por ejemplo {@code APPOINTMENT}
     * @param entityId     id de la entidad afectada, si aplica
     * @param detail       descripción legible de lo ocurrido
     * @param sourceIp     dirección IP de origen de la petición, si se conoce
     * @param registeredAt instante del registro
     */
    public AuditLog(AppUser user, String action, String entity, Long entityId, String detail, String sourceIp,
                    Instant registeredAt) {
        this.user = user;
        this.action = action;
        this.entity = entity;
        this.entityId = entityId;
        this.detail = detail;
        this.sourceIp = sourceIp;
        this.registeredAt = registeredAt;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public String getAction() {
        return action;
    }

    public String getEntity() {
        return entity;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getDetail() {
        return detail;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }
}
