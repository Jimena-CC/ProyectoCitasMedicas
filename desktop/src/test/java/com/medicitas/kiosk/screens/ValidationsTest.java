package com.medicitas.kiosk.screens;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 13);

    @ParameterizedTest
    @CsvSource({
            "DNI, 45871236, true",
            "DNI, 4587123, false",
            "DNI, 458712361, false",
            "DNI, 4587123A, false",
            "CE, 001234567, true",
            "CE, 12345678, false",
            "CE, 1234567890123, false",
            "PAS, AB1234, true",
            "PAS, AB123, false",
            "PAS, AB12-345, false"
    })
    void validatesDocumentByType(String type, String number, boolean valid) {
        assertEquals(valid, Validations.document(type, number).isEmpty());
    }

    @Test
    void emptyDocumentAsksForIt() {
        assertEquals("Ingresa tu número de documento.", Validations.document("DNI", " ").orElseThrow());
    }

    @Test
    void keypadMaxLength() {
        assertEquals(8, Validations.maxLength("DNI"));
        assertEquals(12, Validations.maxLength("CE"));
    }

    @ParameterizedTest
    @CsvSource({"987654321, true", "887654321, false", "98765432, false", "987 654 321, true"})
    void validatesPeruvianMobile(String phone, boolean valid) {
        assertEquals(valid, Validations.phone(phone).isEmpty());
    }

    @Test
    void validatesEmail() {
        assertTrue(Validations.email("lucia.paredes@correo.pe").isEmpty());
        assertFalse(Validations.email("lucia@correo").isEmpty());
    }

    @Test
    void validatesNamesWithAccentsAndSpaces() {
        assertTrue(Validations.name("María José", "nombres").isEmpty());
        assertFalse(Validations.name("L4ura", "nombres").isEmpty());
    }

    @Test
    void birthDateMustBeInThePast() {
        assertTrue(Validations.birthDate(LocalDate.of(1991, 4, 18), TODAY).isEmpty());
        assertFalse(Validations.birthDate(TODAY, TODAY).isEmpty());
        assertFalse(Validations.birthDate(null, TODAY).isEmpty());
    }

    @Test
    void visitReasonIsRequiredAndLimited() {
        assertFalse(Validations.visitReason("").isEmpty());
        assertTrue(Validations.visitReason("Control de presión arterial").isEmpty());
        assertFalse(Validations.visitReason("x".repeat(251)).isEmpty());
    }
}
