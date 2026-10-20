package com.medicitas.api.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.medicitas.api.common.Formats;
import com.medicitas.api.schedule.Slot;
import com.medicitas.api.schedule.SlotStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * Cuerpos JSON de la gestión de agendas: generación, consulta y bloqueo de cupos (issue #46).
 */
public final class AdminSlotDtos {

    private AdminSlotDtos() {
    }

    /**
     * Médico de un cupo.
     *
     * @param id       identificador
     * @param fullName nombres y apellidos
     */
    public record DoctorRef(Long id, String fullName) {
    }

    /**
     * Sede de un cupo.
     *
     * @param id   identificador
     * @param name nombre
     */
    public record LocationRef(Long id, String name) {
    }

    /**
     * Cupo con su estado, tal como lo ve el administrador.
     *
     * @param id        identificador
     * @param date      fecha
     * @param startTime hora de inicio ({@code HH:mm})
     * @param endTime   hora de fin ({@code HH:mm})
     * @param room      consultorio o {@code null}
     * @param status    {@code FREE}, {@code BOOKED} o {@code BLOCKED}
     * @param doctor    médico
     * @param location  sede
     */
    public record SlotDetail(
            Long id,
            LocalDate date,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Formats.TIME) LocalTime startTime,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Formats.TIME) LocalTime endTime,
            String room,
            SlotStatus status,
            DoctorRef doctor,
            LocationRef location) {

        /**
         * Construye la vista del cupo con el estado que tiene en la base.
         *
         * @param slot cupo con médico y sede cargados
         * @return la vista del cupo
         */
        public static SlotDetail from(Slot slot) {
            return from(slot, slot.getStatus());
        }

        /**
         * Construye la vista del cupo con un estado explícito; se usa tras un cambio de estado hecho con una
         * actualización condicional, que no refresca la entidad en memoria.
         *
         * @param slot   cupo con médico y sede cargados
         * @param status estado a mostrar
         * @return la vista del cupo
         */
        public static SlotDetail from(Slot slot, SlotStatus status) {
            return new SlotDetail(slot.getId(), slot.getDate(), slot.getStartTime(), slot.getEndTime(), slot.getRoom(),
                    status, new DoctorRef(slot.getDoctor().getId(), slot.getDoctor().getFullName()),
                    new LocationRef(slot.getLocation().getId(), slot.getLocation().getName()));
        }
    }

    /**
     * Generación de cupos para un médico en una sede.
     *
     * @param doctorId        médico; debe estar activo
     * @param locationId      sede; debe atender la especialidad del médico
     * @param room            consultorio, opcional
     * @param from            primera fecha (inclusive), no anterior a hoy
     * @param to              última fecha (inclusive); como máximo {@link SlotGenerationService#MAX_RANGE_DAYS} días
     * @param startTime       hora del primer cupo de cada día ({@code HH:mm})
     * @param endTime         hora límite: ningún cupo termina después de esta hora ({@code HH:mm})
     * @param weekdays        días de la semana a generar; por defecto de lunes a sábado
     * @param durationMinutes duración de cada cupo; por defecto los minutos por cita de la especialidad
     */
    public record SlotGenerationRequest(
            @NotNull(message = "Selecciona el médico.")
            Long doctorId,

            @NotNull(message = "Selecciona la sede.")
            Long locationId,

            @Size(max = 20, message = "El consultorio no puede superar los 20 caracteres.")
            String room,

            @NotNull(message = "Ingresa la fecha inicial.")
            LocalDate from,

            @NotNull(message = "Ingresa la fecha final.")
            LocalDate to,

            @NotNull(message = "Ingresa la hora de inicio.")
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Formats.TIME)
            LocalTime startTime,

            @NotNull(message = "Ingresa la hora de fin.")
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Formats.TIME)
            LocalTime endTime,

            Set<DayOfWeek> weekdays,

            @Min(value = 5, message = "La duración mínima de un cupo es de 5 minutos.")
            @Max(value = 240, message = "La duración máxima de un cupo es de 240 minutos.")
            Integer durationMinutes) {
    }

    /**
     * Resultado de la generación.
     *
     * @param created cupos creados
     * @param skipped cupos omitidos porque el médico ya tenía un cupo en ese horario
     * @param slots   los cupos creados
     */
    public record SlotGenerationResult(int created, int skipped, List<SlotDetail> slots) {
    }
}
