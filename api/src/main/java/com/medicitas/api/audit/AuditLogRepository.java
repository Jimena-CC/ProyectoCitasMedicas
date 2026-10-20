package com.medicitas.api.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Consulta paginada del registro de auditoría; todo filtro nulo se ignora.
     * Pensada para la pantalla de auditoría del administrador.
     *
     * @param entity   entidad afectada, por ejemplo {@code APPOINTMENT}
     * @param action   acción, por ejemplo {@code APPOINTMENT_BOOKED}
     * @param userId   id del usuario responsable
     * @param from     inicio del rango (inclusive)
     * @param to       fin del rango (exclusive)
     * @param pageable página y tamaño; el orden fijo es del más reciente al más antiguo
     * @return la página de registros con su usuario cargado
     */
    @EntityGraph(attributePaths = "user")
    @Query("""
            select a from AuditLog a left join a.user u
            where (:entity is null or a.entity = :entity)
              and (:action is null or a.action = :action)
              and (:userId is null or u.id = :userId)
              and (:from is null or a.registeredAt >= :from)
              and (:to is null or a.registeredAt < :to)
            order by a.registeredAt desc, a.id desc""")
    Page<AuditLog> search(@Param("entity") String entity, @Param("action") String action,
                          @Param("userId") Long userId, @Param("from") Instant from, @Param("to") Instant to,
                          Pageable pageable);

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
