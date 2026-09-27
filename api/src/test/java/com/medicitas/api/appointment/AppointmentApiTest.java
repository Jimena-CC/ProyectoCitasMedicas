package com.medicitas.api.appointment;

import com.medicitas.api.ApiIntegrationTestSupport;
import com.medicitas.api.notification.Notification;
import com.medicitas.api.notification.NotificationRepository;
import com.medicitas.api.notification.NotificationService;
import com.medicitas.api.notification.NotificationStatus;
import com.medicitas.api.notification.NotificationType;
import com.medicitas.api.notification.SimulatedMessagingClient;
import com.medicitas.api.schedule.Slot;
import com.medicitas.api.schedule.SlotStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppointmentApiTest extends ApiIntegrationTestSupport {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SimulatedMessagingClient simulatedMessagingClient;

    @Test
    void reservaExitosaCreaLaCitaYProgramaSusNotificaciones() throws Exception {
        long patientId = registerPatient();
        Slot slot = nextFreeSlot();

        MvcResult result = book(patientId, slot.getId(), UUID.randomUUID())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.code", matchesPattern("MCA-[A-HJKMNP-Z2-9]{5}")))
                .andExpect(jsonPath("$.date").value(slot.getDate().toString()))
                .andExpect(jsonPath("$.startTime", matchesPattern("\\d{2}:\\d{2}")))
                .andExpect(jsonPath("$.coverage").value("Particular"))
                .andExpect(jsonPath("$.rescheduleCount").value(0))
                .andExpect(jsonPath("$.bookedAt", matchesPattern(".+-05:00")))
                .andReturn();
        long appointmentId = readId(result);

        assertThat(slotStatusOf(slot)).isEqualTo(SlotStatus.BOOKED);
        mockMvc.perform(get("/api/v1/appointments/" + appointmentId + "/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].type", containsInAnyOrder("CONFIRMATION", "REMINDER")));
    }

    @Test
    void dosReservasSimultaneasDelMismoCupoSoloAceptanUna() throws Exception {
        long patient1 = registerPatient();
        long patient2 = registerPatient();
        Slot slot = nextFreeSlot();
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> responses = new ArrayList<>();
            for (long patientId : List.of(patient1, patient2)) {
                Callable<Integer> attempt = () -> {
                    start.await();
                    return book(patientId, slot.getId(), UUID.randomUUID()).andReturn().getResponse().getStatus();
                };
                responses.add(threads.submit(attempt));
            }
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> response : responses) {
                statuses.add(response.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            threads.shutdownNow();
        }
        assertThat(slotStatusOf(slot)).isEqualTo(SlotStatus.BOOKED);
    }

    @Test
    void rechazaUnaSegundaCitaDelPacienteEnElMismoHorario() throws Exception {
        long patientId = registerPatient();
        List<Slot> sameTime = twoSlotsAtTheSameTime();

        book(patientId, sameTime.get(0).getId(), UUID.randomUUID()).andExpect(status().isCreated());
        book(patientId, sameTime.get(1).getId(), UUID.randomUUID())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_APPOINTMENT"));

        assertThat(slotStatusOf(sameTime.get(1))).isEqualTo(SlotStatus.FREE);
    }

    @Test
    void reintentarConLaMismaClaveDevuelveLaMismaCita() throws Exception {
        long patientId = registerPatient();
        Slot slot = nextFreeSlot();
        UUID key = UUID.randomUUID();

        long originalId = readId(book(patientId, slot.getId(), key).andExpect(status().isCreated()).andReturn());
        long retryId = readId(book(patientId, slot.getId(), key).andExpect(status().isOk()).andReturn());

        assertThat(retryId).isEqualTo(originalId);
        mockMvc.perform(get("/api/v1/patients/" + patientId + "/appointments"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void reservarSinIdempotencyKeyEsUnErrorDeValidacion() throws Exception {
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":1,\"slotId\":1,\"visitReason\":\"Control\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATA"))
                .andExpect(jsonPath("$.fields[0].field").value("Idempotency-Key"));
    }

    @Test
    void reprogramarLiberaElCupoOriginalYOcupaElNuevo() throws Exception {
        long patientId = registerPatient();
        Slot original = nextFreeSlot();
        Slot newSlot = freeSlotOfTheSameSpecialty(original);
        long appointmentId = readId(book(patientId, original.getId(), UUID.randomUUID())
                .andExpect(status().isCreated()).andReturn());

        mockMvc.perform(patch("/api/v1/appointments/" + appointmentId + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newSlotId\":" + newSlot.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.date").value(newSlot.getDate().toString()))
                .andExpect(jsonPath("$.rescheduleCount").value(1));

        assertThat(slotStatusOf(original)).isEqualTo(SlotStatus.FREE);
        assertThat(slotStatusOf(newSlot)).isEqualTo(SlotStatus.BOOKED);
    }

    @Test
    void anularLiberaElCupoYNoPermiteAnularDosVeces() throws Exception {
        long patientId = registerPatient();
        Slot slot = nextFreeSlot();
        long appointmentId = readId(book(patientId, slot.getId(), UUID.randomUUID())
                .andExpect(status().isCreated()).andReturn());

        mockMvc.perform(patch("/api/v1/appointments/" + appointmentId + "/cancellation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Viaje fuera de Lima\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(slotStatusOf(slot)).isEqualTo(SlotStatus.FREE);

        mockMvc.perform(patch("/api/v1/appointments/" + appointmentId + "/cancellation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_MODIFIABLE"));
    }

    @Test
    void fallaDelServicioDeMensajeriaNoAfectaLaCitaConfirmada() throws Exception {
        simulatedMessagingClient.setSimulateFailure(true);
        try {
            long patientId = registerPatient();
            Slot slot = nextFreeSlot();
            long appointmentId = readId(book(patientId, slot.getId(), UUID.randomUUID())
                    .andExpect(status().isCreated()).andReturn());

            Notification confirmation = awaitFirstAttempt(appointmentId);
            assertThat(confirmation.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(confirmation.getErrorDetail()).contains("no respondió");

            // Se agotan los 3 intentos configurados.
            notificationService.send(confirmation.getId());
            notificationService.send(confirmation.getId());
            Notification exhausted = notificationRepository.findById(confirmation.getId()).orElseThrow();
            assertThat(exhausted.getStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(exhausted.getAttempts()).isEqualTo((short) 3);

            mockMvc.perform(get("/api/v1/patients/" + patientId + "/appointments"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(appointmentId))
                    .andExpect(jsonPath("$[0].status").value("BOOKED"));
        } finally {
            simulatedMessagingClient.setSimulateFailure(false);
        }
    }

    private Notification awaitFirstAttempt(long appointmentId) throws InterruptedException {
        long limit = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < limit) {
            Optional<Notification> confirmation = notificationRepository
                    .findByAppointmentIdOrderByScheduledForAscIdAsc(appointmentId)
                    .stream()
                    .filter(n -> n.getType() == NotificationType.CONFIRMATION && n.getAttempts() >= 1)
                    .findFirst();
            if (confirmation.isPresent()) {
                return confirmation.get();
            }
            Thread.sleep(100);
        }
        throw new AssertionError("La confirmación no se intentó enviar en 10 segundos");
    }
}
