package com.medicitas.api.audit;

import com.medicitas.api.ApiIntegrationTestSupport;
import com.medicitas.api.appointment.AppointmentHistory;
import com.medicitas.api.appointment.AppointmentHistoryRepository;
import com.medicitas.api.audit.AuditAction.AuditEntities;
import com.medicitas.api.schedule.Slot;
import com.medicitas.api.security.AppUser;
import com.medicitas.api.security.AppUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Toda reserva, reprogramación y anulación deja su fila en {@code audit_log} y su traza en el historial.
 */
@DisplayName("Auditoría de citas")
class AppointmentAuditTest extends ApiIntegrationTestSupport {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AppointmentHistoryRepository appointmentHistoryRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    @DisplayName("Reservar, reprogramar y anular desde el kiosko generan tres registros sin usuario")
    void elCicloDeUnaCitaDesdeElKioskoGeneraTresRegistrosSinUsuario() throws Exception {
        long patientId = registerPatient();
        Slot slot = nextFreeSlot();
        Slot newSlot = freeSlotOfTheSameSpecialty(slot);
        Instant before = Instant.now(clock);

        MvcResult booked = book(patientId, slot.getId(), UUID.randomUUID())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").isString())
                .andReturn();
        long appointmentId = readId(booked);
        mockMvc.perform(patch("/api/v1/appointments/" + appointmentId + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newSlotId":%d}""".formatted(newSlot.getId())))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/appointments/" + appointmentId + "/cancellation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Viaje fuera de Lima"}"""))
                .andExpect(status().isOk());

        List<AuditLog> entries = auditLogRepository
                .findByEntityAndEntityIdOrderByIdAsc(AuditEntities.APPOINTMENT, appointmentId);
        assertThat(entries).hasSize(3);
        assertAll(
                () -> assertThat(entries).extracting(AuditLog::getAction).containsExactly(
                        AuditAction.APPOINTMENT_BOOKED.name(),
                        AuditAction.APPOINTMENT_RESCHEDULED.name(),
                        AuditAction.APPOINTMENT_CANCELLED.name()),
                () -> assertThat(entries).extracting(AuditLog::getUser).containsOnlyNulls(),
                () -> assertThat(entries).extracting(AuditLog::getSourceIp).containsOnly("127.0.0.1"),
                () -> assertThat(entries).allSatisfy(entry -> assertThat(entry.getRegisteredAt()).isAfterOrEqualTo(before)),
                () -> assertThat(entries.get(0).getDetail()).contains("desde el kiosko")
                        .contains("reservada para Prueba Kiosko Integración (DNI ")
                        .contains(" el " + readable(slot)),
                () -> assertThat(entries.get(1).getDetail()).contains("del " + readable(slot))
                        .contains("al " + readable(newSlot)),
                () -> assertThat(entries.get(2).getDetail()).contains("Motivo: Viaje fuera de Lima"),
                // El administrador lee el detalle tal cual: sin IDs internos ni fechas en formato ISO.
                () -> assertThat(entries).extracting(AuditLog::getDetail).allSatisfy(detail -> assertThat(detail)
                        .contains("Prueba Kiosko Integración (DNI ")
                        .doesNotContain("paciente " + patientId)
                        .doesNotContain("cupo " + slot.getId())
                        .doesNotContain("cupo " + newSlot.getId())
                        .doesNotContain(slot.getDate().toString())
                        .doesNotContain(newSlot.getDate().toString())));

        List<AppointmentHistory> history = appointmentHistoryRepository.findByAppointmentIdOrderByIdAsc(appointmentId);
        assertThat(history).hasSize(3).allSatisfy(event -> assertThat(event.getUser()).isNull());
    }

    /** Fecha y hora del cupo como aparecen en el detalle de la auditoría, p. ej. {@code 21/10/2026 09:00}. */
    private static String readable(Slot slot) {
        return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").format(slot.getStart());
    }

    @Test
    @DisplayName("Una reserva hecha por admisión queda atribuida a su usuario y al canal FRONT_DESK")
    void unaReservaDeAdmisionQuedaAtribuidaAlUsuario() throws Exception {
        long patientId = registerPatient();
        Slot slot = nextFreeSlot();
        String token = loginAsFrontDesk();
        AppUser frontDesk = appUserRepository.findByUsernameIgnoreCase("admision").orElseThrow();

        MvcResult booked = book(patientId, slot.getId(), UUID.randomUUID(), token)
                .andExpect(status().isCreated())
                .andReturn();
        long appointmentId = readId(booked);

        List<AuditLog> entries = auditLogRepository
                .findByEntityAndEntityIdOrderByIdAsc(AuditEntities.APPOINTMENT, appointmentId);
        assertThat(entries).hasSize(1);
        AuditLog entry = entries.getFirst();
        assertAll(
                () -> assertThat(entry.getAction()).isEqualTo(AuditAction.APPOINTMENT_BOOKED.name()),
                () -> assertThat(entry.getUser()).isNotNull(),
                () -> assertThat(entry.getUser().getUsername()).isEqualTo("admision"),
                () -> assertThat(entry.getDetail()).contains("por admisión"));

        AppointmentHistory booking = appointmentHistoryRepository.findByAppointmentIdOrderByIdAsc(appointmentId).getFirst();
        assertThat(booking.getUser()).isNotNull();
        assertThat(booking.getUser().getId()).isEqualTo(frontDesk.getId());
    }

    @Test
    @DisplayName("La consulta paginada filtra por entidad, acción, usuario y rango de fechas, y pagina del más reciente al más antiguo")
    void laConsultaPaginadaFiltraPorEntidadAccionUsuarioYFechas() throws Exception {
        long patientId = registerPatient();
        Slot slot = nextFreeSlot();
        String token = loginAsFrontDesk();
        AppUser frontDesk = appUserRepository.findByUsernameIgnoreCase("admision").orElseThrow();
        Instant before = Instant.now(clock);

        long appointmentId = readId(book(patientId, slot.getId(), UUID.randomUUID(), token)
                .andExpect(status().isCreated())
                .andReturn());
        mockMvc.perform(patch("/api/v1/appointments/" + appointmentId + "/cancellation").headers(bearer(token)))
                .andExpect(status().isOk());

        Page<AuditLog> cancellations = auditLogRepository.search(AuditEntities.APPOINTMENT,
                AuditAction.APPOINTMENT_CANCELLED.name(), frontDesk.getId(), before, null, PageRequest.of(0, 50));
        Page<AuditLog> byOtherUser = auditLogRepository.search(AuditEntities.APPOINTMENT, null,
                frontDesk.getId() + 1_000, null, null, PageRequest.of(0, 50));
        Page<AuditLog> beforeTheTest = auditLogRepository.search(null, null, null, null, before, PageRequest.of(0, 50));
        // Esta prueba por sí sola deja al menos tres registros: inicio de sesión, reserva y anulación.
        Page<AuditLog> firstPage = auditLogRepository.search(null, null, null, null, null, PageRequest.of(0, 2));

        assertAll(
                () -> assertThat(cancellations.getContent())
                        .extracting(AuditLog::getEntityId).contains(appointmentId),
                () -> assertThat(cancellations.getContent())
                        .allSatisfy(entry -> assertThat(entry.getUser().getUsername()).isEqualTo("admision")),
                () -> assertThat(byOtherUser.getContent()).isEmpty(),
                () -> assertThat(beforeTheTest.getContent()).extracting(AuditLog::getEntityId).doesNotContain(appointmentId),
                () -> assertThat(firstPage.getContent()).hasSize(2),
                () -> assertThat(firstPage.getTotalElements()).isGreaterThanOrEqualTo(3),
                () -> assertThat(firstPage.getTotalPages()).isGreaterThanOrEqualTo(2),
                () -> assertThat(firstPage.getContent()).isSortedAccordingTo(
                        (a, b) -> b.getRegisteredAt().compareTo(a.getRegisteredAt())));
    }
}
