package com.medicitas.api.admin;

import com.medicitas.api.ApiIntegrationTestSupport;
import com.medicitas.api.audit.AuditAction;
import com.medicitas.api.audit.AuditAction.AuditEntities;
import com.medicitas.api.demo.DemoDataLoader;
import com.medicitas.api.schedule.Slot;
import com.medicitas.api.security.AppUser;
import com.medicitas.api.security.AppUserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consulta paginada de la auditoría por el administrador: filtros, paginación y forma de cada fila.
 * La consulta de repositorio ya se prueba en {@code AppointmentAuditTest} y {@code PostgresSchemaTest};
 * aquí se cubre el endpoint.
 */
@DisplayName("Consulta de auditoría")
class AdminAuditApiTest extends ApiIntegrationTestSupport {

    private static final String AUDIT = "/api/v1/admin/audit";

    @Autowired
    private AppUserRepository appUserRepository;

    @Nested
    @DisplayName("Filtros")
    class Filtros {

        @Test
        @DisplayName("Filtra por entidad, acción, usuario y fechas, y cada fila trae el usuario responsable")
        void filtraPorEntidadAccionUsuarioYFechas() throws Exception {
            String token = loginAsAdmin();
            AppUser admin = appUserRepository.findByUsernameIgnoreCase(DemoDataLoader.ADMIN_USERNAME).orElseThrow();
            LocalDate today = LocalDate.now(clock);

            mockMvc.perform(get(AUDIT).headers(bearer(token))
                            .param("entity", AuditEntities.APP_USER)
                            .param("action", AuditAction.USER_LOGGED_IN.name())
                            .param("userId", admin.getId().toString())
                            .param("from", today.toString())
                            .param("to", today.toString())
                            .param("page", "0")
                            .param("size", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(5))
                    .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)))
                    .andExpect(jsonPath("$.totalPages", greaterThanOrEqualTo(1)))
                    .andExpect(jsonPath("$.content[*].action", everyItem(is("USER_LOGGED_IN"))))
                    .andExpect(jsonPath("$.content[*].entity", everyItem(is("APP_USER"))))
                    .andExpect(jsonPath("$.content[*].user.username", everyItem(is("admin"))))
                    .andExpect(jsonPath("$.content[0].user.id").value(admin.getId()))
                    .andExpect(jsonPath("$.content[0].user.fullName").value("admin"))
                    .andExpect(jsonPath("$.content[0].entityId").value(admin.getId()))
                    .andExpect(jsonPath("$.content[0].detail").value("Inicio de sesión de admin (perfil Administrador)"))
                    .andExpect(jsonPath("$.content[0].sourceIp").value("127.0.0.1"))
                    .andExpect(jsonPath("$.content[0].registeredAt", matchesPattern(".+-05:00")));
        }

        @Test
        @DisplayName("Una acción del kiosko aparece con usuario nulo")
        void unaAccionDelKioskoApareceSinUsuario() throws Exception {
            long patientId = registerPatient();
            Slot slot = nextFreeSlot();
            long appointmentId = readId(book(patientId, slot.getId(), UUID.randomUUID())
                    .andExpect(status().isCreated()).andReturn());

            MvcResult result = mockMvc.perform(get(AUDIT).headers(bearer(loginAsAdmin()))
                            .param("entity", AuditEntities.APPOINTMENT)
                            .param("action", AuditAction.APPOINTMENT_BOOKED.name())
                            .param("size", "100"))
                    .andExpect(status().isOk())
                    .andReturn();

            List<Map<String, Object>> rows = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8),
                    "$.content[?(@.entityId == " + appointmentId + ")]");
            assertThat(rows).hasSize(1);
            Map<String, Object> row = rows.getFirst();
            assertAll(
                    () -> assertThat(row.get("user")).isNull(),
                    () -> assertThat(row.get("action")).isEqualTo("APPOINTMENT_BOOKED"),
                    () -> assertThat((String) row.get("detail")).contains("desde el kiosko"));
        }

        @Test
        @DisplayName("Un usuario sin registros devuelve una página vacía")
        void usuarioSinRegistrosDevuelvePaginaVacia() throws Exception {
            mockMvc.perform(get(AUDIT).headers(bearer(loginAsAdmin())).param("userId", "999999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.totalPages").value(0));
        }
    }

    @Nested
    @DisplayName("Paginación")
    class Paginacion {

        @Test
        @DisplayName("Pagina del registro más reciente al más antiguo con el tamaño pedido")
        void paginaDelMasRecienteAlMasAntiguo() throws Exception {
            String token = loginAsAdmin();
            loginAsFrontDesk();

            MvcResult result = mockMvc.perform(get(AUDIT).headers(bearer(token)).param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.size").value(2))
                    .andExpect(jsonPath("$.totalPages", greaterThanOrEqualTo(2)))
                    .andReturn();
            List<String> timestamps = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8),
                    "$.content[*].registeredAt");
            assertThat(timestamps.get(0)).isGreaterThanOrEqualTo(timestamps.get(1));

            mockMvc.perform(get(AUDIT).headers(bearer(token)).param("size", "2").param("page", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(1))
                    .andExpect(jsonPath("$.content.length()").value(2));
        }

        @ParameterizedTest(name = "{0}={1} falla en el campo {2}")
        @CsvSource({
                "size, 0,   size",
                "size, 101, size",
                "page, -1,  page"
        })
        @DisplayName("Tamaño o página fuera de rango responden 400 INVALID_DATA")
        void parametrosFueraDeRangoResponden400(String param, String value, String field) throws Exception {
            mockMvc.perform(get(AUDIT).headers(bearer(loginAsAdmin())).param(param, value))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_DATA"))
                    .andExpect(jsonPath("$.fields[0].field").value(field));
        }

        @Test
        @DisplayName("Un rango de fechas invertido responde 400 en el campo to")
        void rangoInvertidoResponde400() throws Exception {
            LocalDate today = LocalDate.now(clock);

            mockMvc.perform(get(AUDIT).headers(bearer(loginAsAdmin()))
                            .param("from", today.toString())
                            .param("to", today.minusDays(1).toString()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields[0].field").value("to"));
        }
    }

    @Test
    @DisplayName("Admisión y médicos no consultan la auditoría: 403 FORBIDDEN")
    void otrosRolesReciben403() throws Exception {
        mockMvc.perform(get(AUDIT).headers(bearer(loginAsFrontDesk())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get(AUDIT).headers(bearer(loginAs(DemoDataLoader.DOCTOR_USERNAME_PREFIX + "benavides",
                        DemoDataLoader.DOCTOR_PASSWORD))))
                .andExpect(status().isForbidden());
    }
}
