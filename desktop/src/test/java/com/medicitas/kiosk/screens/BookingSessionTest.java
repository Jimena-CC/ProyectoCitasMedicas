package com.medicitas.kiosk.screens;

import com.medicitas.kiosk.api.Dtos;
import com.medicitas.kiosk.screens.BookingSession.Step;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingSessionTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 14);
    private static final Dtos.Patient LUCIA = new Dtos.Patient(1L, "DNI", "45871236", "Lucía",
            "Paredes Quispe", LocalDate.of(1991, 4, 18), "987654321", "lucia.paredes@correo.pe", null);
    private static final Dtos.Location SAN_ISIDRO = new Dtos.Location(1L, "SI", "Sede San Isidro",
            "Av. Alfredo Salazar 350", "San Isidro");
    private static final Dtos.Location LA_MOLINA = new Dtos.Location(2L, "LM", "Sede La Molina",
            "Av. La Fontana 362", "La Molina");
    private static final Dtos.Specialty CARDIOLOGY = new Dtos.Specialty(4L, "Cardiología", "", 20);
    private static final Dtos.Slot SLOT_118 = slot(118L);

    private BookingSession session;

    @BeforeEach
    void setUp() {
        session = new BookingSession();
    }

    @Test
    void doesNotAdvanceWithoutIdentifyingThePatient() {
        assertFalse(session.advance());
        assertEquals(Step.IDENTIFICATION, session.getCurrentStep());
    }

    @Test
    void walksTheFiveStepsWithCompleteData() {
        reachDateAndTime();
        session.selectSlot(DATE, SLOT_118);
        assertTrue(session.advance());
        assertEquals(Step.CONFIRMATION, session.getCurrentStep());

        assertFalse(session.isStepComplete(Step.CONFIRMATION));
        session.setVisitReason("Control de presión arterial");
        assertTrue(session.isStepComplete(Step.CONFIRMATION));
        assertFalse(session.advance(), "Confirmación es el último paso");
    }

    @Test
    void newPatientMustRegisterAndSetInsurance() {
        session.documentEntered("DNI", "72015893");
        session.patientNotRegistered();
        assertTrue(session.advance());
        assertEquals(Step.PERSONAL_DATA_AND_INSURANCE, session.getCurrentStep());

        assertFalse(session.advance());
        session.patientRegistered(LUCIA);
        assertFalse(session.advance(), "Falta elegir aseguradora o atención particular");
        session.insuranceUpdated(LUCIA);
        assertTrue(session.advance());
    }

    @Test
    void changingLocationClearsSpecialtyAndSlot() {
        reachDateAndTime();
        session.selectSlot(DATE, SLOT_118);

        session.selectLocation(LA_MOLINA);

        assertNull(session.getSpecialty());
        assertNull(session.getSlot());
    }

    @Test
    void idempotencyKeyIsReusedOnRetries() {
        reachDateAndTime();
        session.selectSlot(DATE, SLOT_118);

        UUID first = session.bookingKey();
        assertEquals(first, session.bookingKey());

        session.selectSlot(DATE, SLOT_118);
        assertEquals(first, session.bookingKey(), "Mismo cupo, mismo intento");

        session.selectSlot(DATE, slot(131L));
        assertNotEquals(first, session.bookingKey(), "Otro cupo es un intento nuevo");
    }

    @Test
    void scheduleConflictReturnsToDateAndTimeWithoutSlot() {
        reachDateAndTime();
        session.selectSlot(DATE, SLOT_118);
        session.advance();
        UUID key = session.bookingKey();

        session.scheduleConflict();

        assertEquals(Step.DATE_AND_TIME, session.getCurrentStep());
        assertNull(session.getSlot());
        assertNotEquals(key, session.bookingKey());
    }

    @Test
    void onlyAllowsGoingToVisitedSteps() {
        reachDateAndTime();
        assertFalse(session.goTo(Step.CONFIRMATION));
        assertTrue(session.goTo(Step.IDENTIFICATION));
        assertEquals(Step.IDENTIFICATION, session.getCurrentStep());
    }

    @Test
    void rescheduleStartsAtDateAndTimeAndDoesNotGoBackFurther() {
        session.documentEntered("DNI", "45871236");
        session.patientFound(LUCIA);
        Dtos.Appointment appointment = new Dtos.Appointment(52L, "MCA-7K2Q9", "BOOKED", DATE, LocalTime.of(7, 15),
                LocalTime.of(7, 35), "Cardiología", "Sede San Isidro", "Consultorio 304",
                "Dra. Carla Benavides Ortiz", "Particular", "Control", 0, null);

        session.startReschedule(appointment, SAN_ISIDRO, CARDIOLOGY);

        assertEquals(BookingSession.Mode.RESCHEDULE, session.getMode());
        assertEquals(Step.DATE_AND_TIME, session.getCurrentStep());
        assertFalse(session.goBack());
    }

    @Test
    void clearRemovesPatientData() {
        reachDateAndTime();
        session.clear();

        assertNull(session.getPatient());
        assertNull(session.getDocumentNumber());
        assertNull(session.getLocation());
        assertEquals(Step.IDENTIFICATION, session.getCurrentStep());
        assertEquals(Step.IDENTIFICATION, session.getMaxStep());
    }

    private void reachDateAndTime() {
        session.documentEntered("DNI", "45871236");
        session.patientFound(LUCIA);
        assertTrue(session.advance());
        assertTrue(session.advance());
        session.selectLocation(SAN_ISIDRO);
        session.selectSpecialty(CARDIOLOGY);
        assertTrue(session.advance());
        assertEquals(Step.DATE_AND_TIME, session.getCurrentStep());
    }

    private static Dtos.Slot slot(long id) {
        return new Dtos.Slot(id, LocalTime.of(7, 15), LocalTime.of(7, 35), "Consultorio 304",
                new Dtos.DoctorSummary(7L, "Dra. Carla Benavides Ortiz", "045712"));
    }
}
