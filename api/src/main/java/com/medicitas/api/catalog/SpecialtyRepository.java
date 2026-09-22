package com.medicitas.api.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SpecialtyRepository extends JpaRepository<Specialty, Long> {

    List<Specialty> findByActiveTrueOrderByNameAsc();

    @Query("""
            select ls.specialty from LocationSpecialty ls
            where ls.location.id = :locationId and ls.active = true and ls.specialty.active = true
            order by ls.specialty.name""")
    List<Specialty> findActiveByLocation(@Param("locationId") Long locationId);
}
