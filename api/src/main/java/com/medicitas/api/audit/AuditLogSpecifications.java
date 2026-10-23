package com.medicitas.api.audit;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Criterios de búsqueda de {@link AuditLog} para la consulta paginada del administrador.
 * Solo se agregan a la consulta los filtros informados: así el SQL no lleva parámetros nulos,
 * que PostgreSQL rechaza por no poder inferir su tipo ({@code could not determine data type of parameter}).
 */
public final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    /**
     * Combina los filtros no nulos con {@code AND}; sin ningún filtro devuelve todos los registros.
     *
     * @param entity entidad afectada, por ejemplo {@code APPOINTMENT}, o {@code null} para ignorar
     * @param action acción, por ejemplo {@code APPOINTMENT_BOOKED}, o {@code null} para ignorar
     * @param userId id del usuario responsable, o {@code null} para ignorar
     * @param from   inicio del rango (inclusive), o {@code null} para ignorar
     * @param to     fin del rango (exclusive), o {@code null} para ignorar
     * @return la especificación lista para {@code findAll}
     */
    public static Specification<AuditLog> matching(String entity, String action, Long userId, Instant from,
                                                   Instant to) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (entity != null) {
                predicates.add(builder.equal(root.get("entity"), entity));
            }
            if (action != null) {
                predicates.add(builder.equal(root.get("action"), action));
            }
            if (userId != null) {
                predicates.add(builder.equal(root.get("user").get("id"), userId));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("registeredAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThan(root.get("registeredAt"), to));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
