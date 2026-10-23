package com.medicitas.kiosk.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Dtos: textos derivados para las pantallas")
class DtosTest {

    @Nested
    @DisplayName("Patient")
    class PatientNames {

        @Test
        @DisplayName("el nombre completo une nombres y apellidos")
        void fullNameJoinsBothParts() {
            Dtos.Patient patient = patient("Lucía Fernanda", "Paredes Quispe");

            assertEquals("Lucía Fernanda Paredes Quispe", patient.fullName());
        }

        @Test
        @DisplayName("el nombre completo nunca muestra null si falta una parte")
        void fullNameOmitsMissingParts() {
            assertEquals("Lucía", patient("Lucía", null).fullName());
            assertEquals("", patient(null, null).fullName());
            assertEquals("", patient(null, null).firstName());
        }

        private Dtos.Patient patient(String first, String last) {
            return new Dtos.Patient(1L, "DNI", "45871236", first, last, null, null, null, null);
        }
    }

    @Nested
    @DisplayName("InsuranceSummary")
    class Coverage {

        @ParameterizedTest(name = "{0} se muestra como \"{1}\"")
        @CsvSource({"ACTIVE, vigente", "SUSPENDED, suspendida", "EXPIRED, vencida", "OTHER, ''"})
        @DisplayName("el estado de cobertura se traduce al paciente (RNF-06)")
        void coverageStatusIsTranslated(String status, String expected) {
            assertEquals(expected, summary(status).coverageStatusText());
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("sin estado conocido no se muestra nada")
        void unknownStatusIsBlank(String status) {
            assertEquals("", summary(status).coverageStatusText());
        }

        private Dtos.InsuranceSummary summary(String status) {
            return new Dtos.InsuranceSummary(3L, "Rímac Seguros", "Red Médica", "RM-00458712", status);
        }
    }
}
