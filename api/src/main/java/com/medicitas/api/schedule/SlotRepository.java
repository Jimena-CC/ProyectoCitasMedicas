package com.medicitas.api.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SlotRepository extends JpaRepository<Slot, Long> {

    /**
     * Cambia el estado solo si el cupo sigue en el estado esperado. Devuelve 0 si otro proceso se adelantó.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Slot s set s.status = :newStatus, s.version = s.version + 1
            where s.id = :slotId and s.status = :expectedStatus""")
    int changeStatus(@Param("slotId") Long slotId, @Param("expectedStatus") SlotStatus expectedStatus,
                     @Param("newStatus") SlotStatus newStatus);

    default int take(Long slotId) {
        return changeStatus(slotId, SlotStatus.FREE, SlotStatus.BOOKED);
    }

    default int release(Long slotId) {
        return changeStatus(slotId, SlotStatus.BOOKED, SlotStatus.FREE);
    }

    @Query("""
            select s from Slot s
            join fetch s.doctor d join fetch d.specialty join fetch s.location
            where s.id = :slotId""")
    Optional<Slot> findDetailById(@Param("slotId") Long slotId);

    @Query("""
            select s from Slot s
            join fetch s.doctor d join fetch s.location l
            where d.specialty.id = :specialtyId and l.id = :locationId and d.active = true
              and s.status = :status and s.date between :from and :to
            order by s.date, s.startTime, d.lastNames""")
    List<Slot> findBySpecialtyLocationAndRange(@Param("specialtyId") Long specialtyId,
                                               @Param("locationId") Long locationId,
                                               @Param("status") SlotStatus status,
                                               @Param("from") LocalDate from,
                                               @Param("to") LocalDate to);

    @Query("""
            select s from Slot s
            join fetch s.doctor d join fetch d.specialty join fetch s.location
            where s.status = :status and s.date > :date
            order by s.date, s.startTime, s.id""")
    List<Slot> findWithDetailByStatusAfter(@Param("status") SlotStatus status, @Param("date") LocalDate date);

    @Query("select s.status from Slot s where s.id = :slotId")
    SlotStatus findStatusById(@Param("slotId") Long slotId);
}
