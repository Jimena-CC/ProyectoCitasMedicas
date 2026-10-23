package com.medicitas.api.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Cuerpos JSON de la administración del catálogo: sedes, especialidades y médicos (issue #46).
 */
public final class AdminCatalogDtos {

    private AdminCatalogDtos() {
    }

    /**
     * Sede para los formularios del administrador; incluye las inactivas.
     *
     * @param id       identificador
     * @param code     código corto, por ejemplo {@code SI}
     * @param name     nombre
     * @param address  dirección
     * @param district distrito
     * @param phone    teléfono o {@code null}
     * @param active   si la sede atiende
     */
    public record LocationDetail(Long id, String code, String name, String address, String district, String phone,
                                 boolean active) {
    }

    /**
     * Sede donde se atiende una especialidad.
     *
     * @param id   identificador de la sede
     * @param code código corto
     * @param name nombre
     */
    public record LocationRef(Long id, String code, String name) {
    }

    /**
     * Especialidad con las sedes donde se atiende (relaciones activas).
     *
     * @param id                 identificador
     * @param name               nombre único
     * @param description        descripción visible en el kiosko
     * @param appointmentMinutes duración de cada cita en minutos
     * @param active             si se ofrece en el kiosko
     * @param locations          sedes que la atienden
     */
    public record SpecialtyDetail(Long id, String name, String description, int appointmentMinutes, boolean active,
                                  List<LocationRef> locations) {
    }

    /**
     * Alta o edición de una especialidad.
     *
     * @param name               nombre único (sin distinguir mayúsculas)
     * @param description        descripción, opcional
     * @param appointmentMinutes duración de cada cita, entre 5 y 240 minutos
     * @param locationIds        sedes que atienden la especialidad; las que se omiten al editar quedan inactivas
     * @param active             estado; se ignora en el alta (siempre activa) y, si se omite al editar, se mantiene
     */
    public record SpecialtyRequest(
            @NotBlank(message = "Ingresa el nombre de la especialidad.")
            @Size(max = 120, message = "El nombre no puede superar los 120 caracteres.")
            String name,

            @Size(max = 1000, message = "La descripción no puede superar los 1000 caracteres.")
            String description,

            @NotNull(message = "Ingresa la duración de la cita en minutos.")
            @Min(value = 5, message = "La duración mínima de una cita es de 5 minutos.")
            @Max(value = 240, message = "La duración máxima de una cita es de 240 minutos.")
            Integer appointmentMinutes,

            @NotNull(message = "Indica las sedes donde se atiende la especialidad.")
            List<Long> locationIds,

            Boolean active) {
    }

    /**
     * Especialidad referida desde un médico.
     *
     * @param id   identificador
     * @param name nombre
     */
    public record SpecialtyRef(Long id, String name) {
    }

    /**
     * Médico con su especialidad y estado.
     *
     * @param id                 identificador
     * @param license            número de colegiatura (CMP), único
     * @param specialistRegistry registro de especialista (RNE) o {@code null}
     * @param firstNames         nombres
     * @param lastNames          apellidos
     * @param fullName           nombres y apellidos
     * @param specialty          especialidad que atiende
     * @param active             si atiende; los cupos de un médico inactivo no se muestran en el kiosko
     */
    public record DoctorDetail(Long id, String license, String specialistRegistry, String firstNames,
                               String lastNames, String fullName, SpecialtyRef specialty, boolean active) {
    }

    /**
     * Alta o edición de un médico.
     *
     * @param license            número de colegiatura (CMP), único
     * @param specialistRegistry registro de especialista (RNE), opcional
     * @param firstNames         nombres
     * @param lastNames          apellidos
     * @param specialtyId        especialidad que atiende
     * @param active             estado; se ignora en el alta (siempre activo) y, si se omite al editar, se mantiene
     */
    public record DoctorRequest(
            @NotBlank(message = "Ingresa el número de colegiatura.")
            @Size(max = 10, message = "La colegiatura no puede superar los 10 caracteres.")
            String license,

            @Size(max = 10, message = "El registro de especialista no puede superar los 10 caracteres.")
            String specialistRegistry,

            @NotBlank(message = "Ingresa los nombres del médico.")
            @Size(max = 100, message = "Los nombres no pueden superar los 100 caracteres.")
            String firstNames,

            @NotBlank(message = "Ingresa los apellidos del médico.")
            @Size(max = 100, message = "Los apellidos no pueden superar los 100 caracteres.")
            String lastNames,

            @NotNull(message = "Selecciona la especialidad del médico.")
            Long specialtyId,

            Boolean active) {
    }
}
