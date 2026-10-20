package com.medicitas.api.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;

/**
 * Acceso al registro de auditoría ({@code audit_log}).
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    /** Orden de la consulta paginada: del registro más reciente al más antiguo. */
    Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("registeredAt"), Sort.Order.desc("id"));

    /**
     * Consulta paginada del registro de auditoría; todo filtro nulo se ignora.
     * Pensada para la pantalla de auditoría del administrador. Los filtros se traducen con
     * {@link AuditLogSpecifications#matching} a una consulta que solo incluye los criterios informados,
     * de modo que se ejecuta igual en H2 y en PostgreSQL.
     *
     * @param entity   entidad afectada, por ejemplo {@code APPOINTMENT}
     * @param action   acción, por ejemplo {@code APPOINTMENT_BOOKED}
     * @param userId   id del usuario responsable
     * @param from     inicio del rango (inclusive)
     * @param to       fin del rango (exclusive)
     * @param pageable página y tamaño; el orden fijo es {@link #NEWEST_FIRST} aunque se indique otro
     * @return la página de registros con su usuario cargado
     */
    default Page<AuditLog> search(String entity, String action, Long userId, Instant from, Instant to,
                                  Pageable pageable) {
        Pageable newestFirst = pageable.isPaged()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST)
                : Pageable.unpaged(NEWEST_FIRST);
        return findAll(AuditLogSpecifications.matching(entity, action, userId, from, to), newestFirst);
    }

    /**
     * Página de registros que cumplen la especificación, con el usuario responsable ya cargado.
     *
     * @param specification criterios de búsqueda
     * @param pageable      página, tamaño y orden
     * @return la página de registros con su usuario cargado
     */
    @Override
    @EntityGraph(attributePaths = "user")
    Page<AuditLog> findAll(Specification<AuditLog> specification, Pageable pageable);

    /**
     * Registros de una entidad concreta, del más antiguo al más reciente.
     *
     * @param entity   entidad afectada
     * @param entityId id de la entidad
     * @return los registros con su usuario cargado
     */
    @EntityGraph(attributePaths = "user")
    List<AuditLog> findByEntityAndEntityIdOrderByIdAsc(String entity, Long entityId);
}
