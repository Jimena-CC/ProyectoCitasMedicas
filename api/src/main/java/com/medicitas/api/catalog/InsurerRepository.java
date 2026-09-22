package com.medicitas.api.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface InsurerRepository extends JpaRepository<Insurer, Long> {

    @Query("select i from Insurer i left join fetch i.plans where i.active = true order by i.name")
    List<Insurer> findActiveWithPlans();
}
