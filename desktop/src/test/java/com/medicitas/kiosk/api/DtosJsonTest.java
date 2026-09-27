package com.medicitas.kiosk.api;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static com.medicitas.kiosk.api.ApiClient.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifica que los ejemplos de docs/api-contract.md se lean correctamente. */
class DtosJsonTest {

    @Test
    void readsPatientWithInsurance() throws Exception {
        String json = """
                {
                  "id": 1, "documentType": "DNI", "documentNumber": "45871236",
                  "firstNames": "Lucía", "lastNames": "Paredes Quispe", "birthDate": "1991-04-18",
                  "phone": "987654321", "email": "lucia.paredes@correo.pe",
                  "insurance": { "patientInsuranceId": 3, "insurer": "Rímac Seguros", "plan": "Red Médica",
                                 "policyNumber": "RM-00458712", "coverageStatus": "ACTIVE" }
                }""";

        Dtos.Patient patient = JSON.readValue(json, Dtos.Patient.class);

        assertEquals(LocalDate.of(1991, 4, 18), patient.birthDate());
        assertEquals("Lucía", patient.firstName());
        assertEquals("Rímac Seguros · Red Médica", patient.insurance().description());
    }

    @Test
    void readsSelfPayPatientAndToleratesNewFields() throws Exception {
        String json = """
                { "id": 2, "documentType": "DNI", "documentNumber": "70123456", "firstNames": "Diego Andrés",
                  "lastNames": "Salazar", "insurance": null, "futureField": true }""";

        Dtos.Patient patient = JSON.readValue(json, Dtos.Patient.class);

        assertNull(patient.insurance());
        assertEquals("Diego", patient.firstName());
    }

    @Test
    void readsInsurersWithPlans() throws Exception {
        String json = """
                [ { "id": 1, "name": "Rímac Seguros", "plans": [
                    { "id": 1, "name": "Red Preferente", "consultationCopay": 45.00 },
                    { "id": 2, "name": "Red Médica", "consultationCopay": 60.00 } ] } ]""";

        List<Dtos.Insurer> list = JSON.readValue(json, new TypeReference<>() { });

        assertEquals(2, list.get(0).plans().size());
        assertEquals(0, new BigDecimal("60.00").compareTo(list.get(0).plans().get(1).consultationCopay()));
    }

    @Test
    void readsAvailabilityWithShortTimes() throws Exception {
        String json = """
                { "days": [ { "date": "2026-09-14", "freeSlots": 9, "slots": [
                  { "slotId": 118, "startTime": "07:15", "endTime": "07:35", "room": "Consultorio 304",
                    "doctor": { "id": 7, "fullName": "Dra. Carla Benavides Ortiz", "license": "045712" } } ] },
                  { "date": "2026-09-15", "freeSlots": 0, "slots": [] } ] }""";

        Dtos.Availability availability = JSON.readValue(json, Dtos.Availability.class);

        Dtos.Slot slot = availability.days().get(0).slots().get(0);
        assertEquals(LocalTime.of(7, 15), slot.startTime());
        assertEquals("045712", slot.doctor().license());
        assertTrue(availability.days().get(1).slots().isEmpty());
    }

    @Test
    void readsBookedAppointment() throws Exception {
        String json = """
                { "id": 52, "code": "MCA-7K2Q9", "status": "BOOKED", "date": "2026-09-14",
                  "startTime": "07:15", "endTime": "07:35", "specialty": "Cardiología",
                  "location": "Sede San Isidro", "room": "Consultorio 304",
                  "doctor": "Dra. Carla Benavides Ortiz", "coverage": "Rímac Seguros · Red Médica",
                  "visitReason": "Control de presión arterial", "rescheduleCount": 0,
                  "bookedAt": "2026-09-13T21:42:10-05:00" }""";

        Dtos.Appointment appointment = JSON.readValue(json, Dtos.Appointment.class);

        assertEquals("MCA-7K2Q9", appointment.code());
        assertEquals(-5 * 3600, appointment.bookedAt().getOffset().getTotalSeconds());
    }

    @Test
    void writesNewAppointmentAndIsoDates() throws Exception {
        String json = JSON.writeValueAsString(new Dtos.NewAppointment(1L, 118L, null, "Control"));
        assertTrue(json.contains("\"slotId\":118"));

        String patient = JSON.writeValueAsString(new Dtos.NewPatient("DNI", "72015893", "Diego", "Salazar",
                LocalDate.of(1988, 11, 2), "M", "956112233", "diego@correo.pe", true));
        assertTrue(patient.contains("\"birthDate\":\"1988-11-02\""));
    }

    @Test
    void readsConflictError() throws Exception {
        String json = """
                { "code": "SLOT_NOT_AVAILABLE",
                  "message": "Ese horario acaba de ser reservado por otra persona. Elige otro horario disponible.",
                  "fields": [], "timestamp": "2026-09-13T21:42:10-05:00" }""";

        Dtos.ApiError error = JSON.readValue(json, Dtos.ApiError.class);
        ApiException exception = new ApiException(409, error.code(), error.message(), error.fields());

        assertTrue(exception.isScheduleConflict());
        assertTrue(exception.getMessage().startsWith("Ese horario"));
    }

    @Test
    void noConnectionUsesPatientFacingMessage() {
        ApiException exception = ApiException.from(new RuntimeException(ApiException.noConnection(new Exception())));
        assertTrue(exception.isNoConnection());
        assertEquals(ApiException.NO_CONNECTION_MESSAGE, exception.getMessage());
    }
}
