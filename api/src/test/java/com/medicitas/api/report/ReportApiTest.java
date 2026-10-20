package com.medicitas.api.report;

import com.medicitas.api.ApiIntegrationTestSupport;
import com.medicitas.api.appointment.Appointment;
import com.medicitas.api.appointment.AppointmentRepository;
import com.medicitas.api.appointment.AppointmentStatus;
import com.medicitas.api.appointment.BookingChannel;
import com.medicitas.api.catalog.Doctor;
import com.medicitas.api.catalog.DoctorRepository;
import com.medicitas.api.catalog.Location;
import com.medicitas.api.catalog.LocationRepository;
import com.medicitas.api.demo.DemoDataLoader;
import com.medicitas.api.patient.Patient;
import com.medicitas.api.patient.PatientRepository;
import com.medicitas.api.schedule.Slot;
import com.medicitas.api.schedule.SlotStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reportes del administrador sobre un escenario conocido: se crean cupos y citas con estados fijos en un rango
 * de fechas que ninguna otra prueba usa (dos meses adelante) y se comprueba que las cifras coinciden exactamente.
 *
 * <p>Escenario (fecha {@code from}): Cardiología en San Isidro con 5 cupos (reservada, atendida, atendida,
 * no asistió, anulada) y 1 cupo bloqueado; Cardiología en La Molina con 2 cupos (atendida, libre);
 * Dermatología en San Isidro con 2 cupos (no asistió, no asistió).
 */
@DisplayName("Reportes")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportApiTest extends ApiIntegrationTestSupport {

    private static final String REPORTS = "/api/v1/admin/reports";

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    private LocalDate from;
    private LocalDate to;
    private Long cardiologyId;
    private Long dermatologyId;
    private Long laMolinaId;

    @BeforeAll
    void createScenario() throws Exception {
        from = LocalDate.now(clock).plusDays(60);
        to = from.plusDays(1);
        List<Doctor> doctors = doctorRepository.findAllWithSpecialty();
        Doctor cardiologistSi = byLastName(doctors, "Benavides");
        Doctor cardiologistLm = byLastName(doctors, "Palomino");
        Doctor dermatologistSi = byLastName(doctors, "Villanueva");
        cardiologyId = cardiologistSi.getSpecialty().getId();
        dermatologyId = dermatologistSi.getSpecialty().getId();
        List<Location> locations = locationRepository.findByActiveTrueOrderByIdAsc();
        Location sanIsidro = locations.get(0);
        Location laMolina = locations.get(1);
        laMolinaId = laMolina.getId();
        Patient patient = patientRepository.findById(registerPatient()).orElseThrow();

        appointment(patient, slot(cardiologistSi, sanIsidro, 0, SlotStatus.BOOKED), AppointmentStatus.BOOKED);
        appointment(patient, slot(cardiologistSi, sanIsidro, 1, SlotStatus.BOOKED), AppointmentStatus.ATTENDED);
        appointment(patient, slot(cardiologistSi, sanIsidro, 2, SlotStatus.BOOKED), AppointmentStatus.ATTENDED);
        appointment(patient, slot(cardiologistSi, sanIsidro, 3, SlotStatus.BOOKED), AppointmentStatus.NO_SHOW);
        appointment(patient, slot(cardiologistSi, sanIsidro, 4, SlotStatus.FREE), AppointmentStatus.CANCELLED);
        slot(cardiologistSi, sanIsidro, 5, SlotStatus.BLOCKED);
        appointment(patient, slot(cardiologistLm, laMolina, 0, SlotStatus.BOOKED), AppointmentStatus.ATTENDED);
        slot(cardiologistLm, laMolina, 1, SlotStatus.FREE);
        appointment(patient, slot(dermatologistSi, sanIsidro, 0, SlotStatus.BOOKED), AppointmentStatus.NO_SHOW);
        appointment(patient, slot(dermatologistSi, sanIsidro, 1, SlotStatus.BOOKED), AppointmentStatus.NO_SHOW);
    }

    private static Doctor byLastName(List<Doctor> doctors, String lastName) {
        return doctors.stream().filter(doctor -> doctor.getLastNames().startsWith(lastName)).findFirst().orElseThrow();
    }

    /** Cupo de 20 minutos en la fecha {@code from}, desde las 15:00, en el estado indicado. */
    private Slot slot(Doctor doctor, Location location, int index, SlotStatus status) {
        LocalTime start = LocalTime.of(15, 0).plusMinutes(index * 20L);
        Slot slot = new Slot(doctor, location, from, start, start.plusMinutes(20), "Consultorio Reportes");
        slot.setStatus(status);
        return slotRepository.save(slot);
    }

    private void appointment(Patient patient, Slot slot, AppointmentStatus status) {
        Instant now = Instant.now(clock);
        Appointment appointment = new Appointment("RPT-" + UUID.randomUUID().toString().substring(0, 5).toUpperCase(),
                patient, slot, null, "Cita de prueba para reportes", BookingChannel.FRONT_DESK, UUID.randomUUID(), now);
        switch (status) {
            case CANCELLED -> appointment.cancel(now);
            case ATTENDED, NO_SHOW -> appointment.registerOutcome(status, now);
            case BOOKED -> { }
        }
        appointmentRepository.save(appointment);
    }

    private ResultActions report(String token, String path, String... extraParams) throws Exception {
        var request = get(REPORTS + path).headers(bearer(token))
                .param("from", from.toString())
                .param("to", to.toString());
        for (int i = 0; i < extraParams.length; i += 2) {
            request.param(extraParams[i], extraParams[i + 1]);
        }
        return mockMvc.perform(request);
    }

    @Nested
    @DisplayName("Citas por estado")
    class CitasPorEstado {

        @Test
        @DisplayName("Los totales y los desgloses por especialidad y sede coinciden con las citas registradas")
        void totalesYDesgloses() throws Exception {
            report(loginAsAdmin(), "/appointments")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.from").value(from.toString()))
                    .andExpect(jsonPath("$.to").value(to.toString()))
                    .andExpect(jsonPath("$.totals.booked").value(1))
                    .andExpect(jsonPath("$.totals.attended").value(3))
                    .andExpect(jsonPath("$.totals.cancelled").value(1))
                    .andExpect(jsonPath("$.totals.noShow").value(3))
                    .andExpect(jsonPath("$.totals.total").value(8))
                    .andExpect(jsonPath("$.bySpecialty.length()").value(2))
                    .andExpect(jsonPath("$.bySpecialty[0].specialtyName").value("Cardiología"))
                    .andExpect(jsonPath("$.bySpecialty[0].totals.booked").value(1))
                    .andExpect(jsonPath("$.bySpecialty[0].totals.attended").value(3))
                    .andExpect(jsonPath("$.bySpecialty[0].totals.cancelled").value(1))
                    .andExpect(jsonPath("$.bySpecialty[0].totals.noShow").value(1))
                    .andExpect(jsonPath("$.bySpecialty[0].totals.total").value(6))
                    .andExpect(jsonPath("$.bySpecialty[1].specialtyName").value("Dermatología"))
                    .andExpect(jsonPath("$.bySpecialty[1].totals.noShow").value(2))
                    .andExpect(jsonPath("$.bySpecialty[1].totals.total").value(2))
                    .andExpect(jsonPath("$.byLocation.length()").value(2))
                    .andExpect(jsonPath("$.byLocation[0].locationName").value("Sede La Molina"))
                    .andExpect(jsonPath("$.byLocation[0].totals.attended").value(1))
                    .andExpect(jsonPath("$.byLocation[0].totals.total").value(1))
                    .andExpect(jsonPath("$.byLocation[1].locationName").value("Sede San Isidro"))
                    .andExpect(jsonPath("$.byLocation[1].totals.total").value(7));
        }

        @Test
        @DisplayName("Los filtros por especialidad y por sede reducen el reporte")
        void filtrosPorEspecialidadYSede() throws Exception {
            String token = loginAsAdmin();

            report(token, "/appointments", "specialtyId", dermatologyId.toString())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totals.total").value(2))
                    .andExpect(jsonPath("$.bySpecialty.length()").value(1));
            report(token, "/appointments", "locationId", laMolinaId.toString())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totals.total").value(1))
                    .andExpect(jsonPath("$.totals.attended").value(1));
            report(token, "/appointments", "specialtyId", "999999")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }
    }

    @Test
    @DisplayName("La tasa de inasistencia es noShow / (atendidas + noShow) por especialidad y en total")
    void tasaDeInasistencia() throws Exception {
        report(loginAsAdmin(), "/no-show-rate")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bySpecialty.length()").value(2))
                .andExpect(jsonPath("$.bySpecialty[0].specialtyId").value(cardiologyId))
                .andExpect(jsonPath("$.bySpecialty[0].attended").value(3))
                .andExpect(jsonPath("$.bySpecialty[0].noShow").value(1))
                .andExpect(jsonPath("$.bySpecialty[0].rate").value(0.25))
                .andExpect(jsonPath("$.bySpecialty[1].specialtyName").value("Dermatología"))
                .andExpect(jsonPath("$.bySpecialty[1].attended").value(0))
                .andExpect(jsonPath("$.bySpecialty[1].noShow").value(2))
                .andExpect(jsonPath("$.bySpecialty[1].rate").value(1.0))
                .andExpect(jsonPath("$.total.attended").value(3))
                .andExpect(jsonPath("$.total.noShow").value(3))
                .andExpect(jsonPath("$.total.rate").value(0.5));
    }

    @Test
    @DisplayName("La ocupación cuenta citas reservadas, atendidas y no asistidas sobre los cupos no bloqueados")
    void ocupacion() throws Exception {
        report(loginAsAdmin(), "/occupancy")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(3))
                .andExpect(jsonPath("$.rows[0].specialtyName").value("Cardiología"))
                .andExpect(jsonPath("$.rows[0].locationName").value("Sede La Molina"))
                .andExpect(jsonPath("$.rows[0].slotsTotal").value(2))
                .andExpect(jsonPath("$.rows[0].slotsBlocked").value(0))
                .andExpect(jsonPath("$.rows[0].slotsBooked").value(1))
                .andExpect(jsonPath("$.rows[0].rate").value(0.5))
                .andExpect(jsonPath("$.rows[1].locationName").value("Sede San Isidro"))
                .andExpect(jsonPath("$.rows[1].slotsTotal").value(5))
                .andExpect(jsonPath("$.rows[1].slotsBlocked").value(1))
                .andExpect(jsonPath("$.rows[1].slotsBooked").value(4))
                .andExpect(jsonPath("$.rows[1].rate").value(0.8))
                .andExpect(jsonPath("$.rows[2].specialtyName").value("Dermatología"))
                .andExpect(jsonPath("$.rows[2].slotsTotal").value(2))
                .andExpect(jsonPath("$.rows[2].slotsBooked").value(2))
                .andExpect(jsonPath("$.rows[2].rate").value(1.0))
                .andExpect(jsonPath("$.total.slotsTotal").value(9))
                .andExpect(jsonPath("$.total.slotsBlocked").value(1))
                .andExpect(jsonPath("$.total.slotsBooked").value(7))
                .andExpect(jsonPath("$.total.rate").value(0.7778));
    }

    @Nested
    @DisplayName("Exportación CSV")
    class ExportacionCsv {

        private String csv(MvcResult result) {
            return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        }

        @Test
        @DisplayName("El CSV de inasistencia lleva BOM UTF-8, cabecera, una fila por especialidad y la fila total")
        void csvDeInasistencia() throws Exception {
            MvcResult result = report(loginAsAdmin(), "/export", "report", "no-show-rate")
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", startsWith("text/csv")))
                    .andExpect(header().string("Content-Disposition", containsString("attachment")))
                    .andExpect(header().string("Content-Disposition",
                            containsString("reporte-inasistencia_" + from + "_" + to + ".csv")))
                    .andReturn();

            String content = csv(result);
            List<String> lines = content.substring(1).lines().toList();
            assertAll(
                    () -> assertThat(content).startsWith("﻿"),
                    () -> assertThat(lines.getFirst()).isEqualTo("especialidad,atendidas,inasistencias,tasa_inasistencia"),
                    () -> assertThat(lines).contains("Cardiología,3,1,0.2500", "Dermatología,0,2,1.0000"),
                    () -> assertThat(lines.getLast()).isEqualTo("Total,3,3,0.5000"));
        }

        @Test
        @DisplayName("Los CSV de citas y ocupación reproducen las mismas cifras que el JSON")
        void csvDeCitasYOcupacion() throws Exception {
            String token = loginAsAdmin();

            List<String> appointments = csv(report(token, "/export", "report", "appointments")
                    .andExpect(status().isOk()).andReturn()).substring(1).lines().toList();
            List<String> occupancy = csv(report(token, "/export", "report", "occupancy")
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition", containsString("reporte-ocupacion_")))
                    .andReturn()).substring(1).lines().toList();

            assertAll(
                    () -> assertThat(appointments.getFirst())
                            .isEqualTo("grupo,nombre,reservadas,atendidas,anuladas,inasistencias,total"),
                    () -> assertThat(appointments).contains("Especialidad,Cardiología,1,3,1,1,6",
                            "Especialidad,Dermatología,0,0,0,2,2", "Sede,Sede La Molina,0,1,0,0,1",
                            "Sede,Sede San Isidro,1,2,1,3,7"),
                    () -> assertThat(appointments.getLast()).isEqualTo("Total,Todas,1,3,1,3,8"),
                    () -> assertThat(occupancy.getFirst())
                            .isEqualTo("especialidad,sede,cupos,cupos_bloqueados,cupos_ocupados,tasa_ocupacion"),
                    () -> assertThat(occupancy).contains("Cardiología,Sede San Isidro,5,1,4,0.8000"),
                    () -> assertThat(occupancy.getLast()).isEqualTo("Total,Todas,9,1,7,0.7778"));
        }

        @Test
        @DisplayName("Un nombre de reporte desconocido responde 400 en el campo report")
        void reporteDesconocidoResponde400() throws Exception {
            report(loginAsAdmin(), "/export", "report", "ventas")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_DATA"))
                    .andExpect(jsonPath("$.fields[0].field").value("report"));
        }
    }

    @Nested
    @DisplayName("Validación y acceso")
    class ValidacionYAcceso {

        @ParameterizedTest(name = "{0}")
        @CsvSource({
                "/appointments",
                "/no-show-rate",
                "/occupancy"
        })
        @DisplayName("Un rango invertido o mayor a 366 días responde 400 en el campo to")
        void rangosInvalidosResponden400(String path) throws Exception {
            String token = loginAsAdmin();

            mockMvc.perform(get(REPORTS + path).headers(bearer(token))
                            .param("from", from.toString()).param("to", from.minusDays(1).toString()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields[0].field").value("to"));
            mockMvc.perform(get(REPORTS + path).headers(bearer(token))
                            .param("from", from.toString()).param("to", from.plusDays(366).toString()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("El reporte puede abarcar como máximo 366 días."));
            mockMvc.perform(get(REPORTS + path).headers(bearer(token))
                            .param("from", from.toString()).param("to", from.plusDays(365).toString()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Sin fechas responde 400 y admisión o médicos reciben 403")
        void sinFechasYOtrosRoles() throws Exception {
            mockMvc.perform(get(REPORTS + "/appointments").headers(bearer(loginAsAdmin())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields[0].field").value("from"));
            report(loginAsFrontDesk(), "/appointments")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            report(loginAs(DemoDataLoader.DOCTOR_USERNAME_PREFIX + "benavides", DemoDataLoader.DOCTOR_PASSWORD),
                    "/export", "report", "occupancy")
                    .andExpect(status().isForbidden());
        }
    }
}
