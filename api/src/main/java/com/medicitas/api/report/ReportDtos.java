package com.medicitas.api.report;

import com.medicitas.api.appointment.Appointment;
import com.medicitas.api.appointment.AppointmentStatus;
import com.medicitas.api.catalog.Doctor;
import com.medicitas.api.schedule.Slot;
import com.medicitas.api.schedule.SlotStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * Respuestas de los reportes del administrador (issue #46, RF-24 a RF-26) y los hechos sobre los que se calculan.
 */
public final class ReportDtos {

    private ReportDtos() {
    }

    /**
     * Cita reducida a lo que los reportes necesitan.
     *
     * @param specialtyId   especialidad del médico de la cita
     * @param specialtyName nombre de la especialidad
     * @param locationId    sede del cupo
     * @param locationName  nombre de la sede
     * @param status        estado de la cita
     */
    public record AppointmentFact(Long specialtyId, String specialtyName, Long locationId, String locationName,
                                  AppointmentStatus status) {

        /**
         * Extrae el hecho de una cita con su cupo, médico, especialidad y sede cargados.
         *
         * @param appointment cita
         * @return el hecho
         */
        public static AppointmentFact from(Appointment appointment) {
            Slot slot = appointment.getSlot();
            Doctor doctor = slot.getDoctor();
            return new AppointmentFact(doctor.getSpecialty().getId(), doctor.getSpecialty().getName(),
                    slot.getLocation().getId(), slot.getLocation().getName(), appointment.getStatus());
        }
    }

    /**
     * Cupo reducido a lo que el reporte de ocupación necesita.
     *
     * @param specialtyId   especialidad del médico
     * @param specialtyName nombre de la especialidad
     * @param locationId    sede
     * @param locationName  nombre de la sede
     * @param status        estado del cupo
     */
    public record SlotFact(Long specialtyId, String specialtyName, Long locationId, String locationName,
                           SlotStatus status) {

        /**
         * Extrae el hecho de un cupo con su médico, especialidad y sede cargados.
         *
         * @param slot cupo
         * @return el hecho
         */
        public static SlotFact from(Slot slot) {
            Doctor doctor = slot.getDoctor();
            return new SlotFact(doctor.getSpecialty().getId(), doctor.getSpecialty().getName(),
                    slot.getLocation().getId(), slot.getLocation().getName(), slot.getStatus());
        }
    }

    /**
     * Conteo de citas por estado.
     *
     * @param booked    reservadas (pendientes de atención)
     * @param attended  atendidas
     * @param cancelled anuladas
     * @param noShow    no asistidas
     * @param total     suma de las anteriores
     */
    public record StatusTotals(long booked, long attended, long cancelled, long noShow, long total) {

        /** Totales vacíos. */
        public static final StatusTotals EMPTY = new StatusTotals(0, 0, 0, 0, 0);

        /**
         * Suma una cita en el estado indicado.
         *
         * @param status estado de la cita
         * @return los totales con la cita sumada
         */
        public StatusTotals plus(AppointmentStatus status) {
            return new StatusTotals(
                    booked + (status == AppointmentStatus.BOOKED ? 1 : 0),
                    attended + (status == AppointmentStatus.ATTENDED ? 1 : 0),
                    cancelled + (status == AppointmentStatus.CANCELLED ? 1 : 0),
                    noShow + (status == AppointmentStatus.NO_SHOW ? 1 : 0),
                    total + 1);
        }
    }

    /**
     * Citas por estado de una especialidad.
     *
     * @param specialtyId   especialidad
     * @param specialtyName nombre
     * @param totals        conteo por estado
     */
    public record SpecialtyStatusRow(Long specialtyId, String specialtyName, StatusTotals totals) {
    }

    /**
     * Citas por estado de una sede.
     *
     * @param locationId   sede
     * @param locationName nombre
     * @param totals       conteo por estado
     */
    public record LocationStatusRow(Long locationId, String locationName, StatusTotals totals) {
    }

    /**
     * Reporte de citas por estado (RF-24).
     *
     * @param from        primera fecha de cita (inclusive)
     * @param to          última fecha de cita (inclusive)
     * @param totals      conteo general
     * @param bySpecialty desglose por especialidad, ordenado por nombre
     * @param byLocation  desglose por sede, ordenado por nombre
     */
    public record AppointmentsReport(LocalDate from, LocalDate to, StatusTotals totals,
                                     List<SpecialtyStatusRow> bySpecialty, List<LocationStatusRow> byLocation) {
    }

    /**
     * Tasa de inasistencia de una especialidad: {@code noShow / (attended + noShow)}, o 0 si no hubo atenciones.
     *
     * @param specialtyId   especialidad
     * @param specialtyName nombre
     * @param attended      citas atendidas
     * @param noShow        citas no asistidas
     * @param rate          tasa entre 0 y 1, con cuatro decimales
     */
    public record NoShowRow(Long specialtyId, String specialtyName, long attended, long noShow, double rate) {
    }

    /**
     * Tasa de inasistencia general.
     *
     * @param attended citas atendidas
     * @param noShow   citas no asistidas
     * @param rate     tasa entre 0 y 1, con cuatro decimales
     */
    public record NoShowTotals(long attended, long noShow, double rate) {
    }

    /**
     * Reporte de tasa de inasistencia por especialidad (RF-25).
     *
     * @param from        primera fecha de cita (inclusive)
     * @param to          última fecha de cita (inclusive)
     * @param bySpecialty filas por especialidad, ordenadas por nombre
     * @param total       tasa general
     */
    public record NoShowRateReport(LocalDate from, LocalDate to, List<NoShowRow> bySpecialty, NoShowTotals total) {
    }

    /**
     * Ocupación de una especialidad en una sede: {@code slotsBooked / slotsTotal}, o 0 si no hay cupos.
     *
     * @param specialtyId   especialidad
     * @param specialtyName nombre de la especialidad
     * @param locationId    sede
     * @param locationName  nombre de la sede
     * @param slotsTotal    cupos generados en el rango, sin contar los bloqueados
     * @param slotsBlocked  cupos bloqueados en el rango (se informan aparte)
     * @param slotsBooked   citas en estado reservada, atendida o no asistida
     * @param rate          tasa entre 0 y 1, con cuatro decimales
     */
    public record OccupancyRow(Long specialtyId, String specialtyName, Long locationId, String locationName,
                               long slotsTotal, long slotsBlocked, long slotsBooked, double rate) {
    }

    /**
     * Ocupación general.
     *
     * @param slotsTotal   cupos generados, sin contar los bloqueados
     * @param slotsBlocked cupos bloqueados
     * @param slotsBooked  citas reservadas, atendidas o no asistidas
     * @param rate         tasa entre 0 y 1, con cuatro decimales
     */
    public record OccupancyTotals(long slotsTotal, long slotsBlocked, long slotsBooked, double rate) {
    }

    /**
     * Reporte de ocupación por especialidad y sede (RF-26).
     *
     * @param from  primera fecha (inclusive)
     * @param to    última fecha (inclusive)
     * @param rows  filas por especialidad y sede, ordenadas por especialidad y sede
     * @param total ocupación general
     */
    public record OccupancyReport(LocalDate from, LocalDate to, List<OccupancyRow> rows, OccupancyTotals total) {
    }

    /**
     * Archivo CSV listo para descargar.
     *
     * @param fileName nombre sugerido, por ejemplo {@code reporte-citas_2026-10-01_2026-10-31.csv}
     * @param content  bytes UTF-8 con BOM
     */
    public record CsvFile(String fileName, byte[] content) {
    }
}
