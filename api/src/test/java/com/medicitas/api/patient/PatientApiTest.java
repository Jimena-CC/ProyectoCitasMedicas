package com.medicitas.api.patient;

import com.medicitas.api.ApiIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PatientApiTest extends ApiIntegrationTestSupport {

    @Test
    void identificaAlPacienteDemoConSuSeguroVigente() throws Exception {
        mockMvc.perform(get("/api/v1/patients").param("documentType", "DNI").param("documentNumber", "45871236"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastNames").value("Paredes Quispe"))
                .andExpect(jsonPath("$.birthDate").value("1991-04-18"))
                .andExpect(jsonPath("$.insurance.policyNumber").value("RM-00458712"))
                .andExpect(jsonPath("$.insurance.coverageStatus").value("ACTIVE"));
    }

    @Test
    void documentoInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/api/v1/patients").param("documentType", "DNI").param("documentNumber", "11111111"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
    }

    @Test
    void rechazaRegistrarUnDocumentoExistente() throws Exception {
        String body = """
                {"documentType":"DNI","documentNumber":"45871236","firstNames":"Otra","lastNames":"Persona",
                 "birthDate":"1990-01-01","phone":"911111111","email":"otra@correo.pe"}""";
        mockMvc.perform(post("/api/v1/patients").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_DOCUMENT"))
                .andExpect(jsonPath("$.fields").isEmpty());
    }

    @Test
    void errorDeValidacionUsaElFormatoDelContrato() throws Exception {
        String body = """
                {"documentType":"DNI","documentNumber":"12345678","firstNames":"","lastNames":"Rojas",
                 "birthDate":"1990-01-01","phone":"12345","email":"no-es-correo"}""";
        mockMvc.perform(post("/api/v1/patients").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATA"))
                .andExpect(jsonPath("$.message", not(emptyString())))
                .andExpect(jsonPath("$.fields.length()").value(3))
                .andExpect(jsonPath("$.fields[*].field", hasItem("phone")))
                .andExpect(jsonPath("$.fields[*].field", hasItem("email")))
                .andExpect(jsonPath("$.fields[*].field", hasItem("firstNames")))
                .andExpect(jsonPath("$.timestamp", matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}-05:00")))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void rechazaUnDniConFormatoIncorrecto() throws Exception {
        String body = """
                {"documentType":"DNI","documentNumber":"1234","firstNames":"Ana","lastNames":"Rojas",
                 "birthDate":"1990-01-01","phone":"912345678","email":"ana@correo.pe"}""";
        mockMvc.perform(post("/api/v1/patients").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATA"))
                .andExpect(jsonPath("$.fields[0].field").value("documentNumber"));
    }

    @Test
    void registraPacienteNuevoYLuegoLoIdentifica() throws Exception {
        long id = registerPatient();
        mockMvc.perform(get("/api/v1/patients/" + id + "/appointments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
