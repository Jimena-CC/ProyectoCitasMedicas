package com.medicitas.api.report;

import com.medicitas.api.ApiIntegrationTestSupport;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exportación de reportes a Excel ({@code format=xlsx}) sobre los datos de demostración. Solo comprueba la forma
 * del archivo y los permisos; las cifras exactas las cubre {@link ReportApiTest} con un escenario conocido.
 */
@DisplayName("Reportes - exportación a Excel")
class ReportXlsxApiTest extends ApiIntegrationTestSupport {

    private static final String EXPORT = "/api/v1/admin/reports/export";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private MockHttpServletRequestBuilder exportRequest(String report, String... extraParams) {
        LocalDate from = LocalDate.now(clock);
        var request = get(EXPORT)
                .param("report", report)
                .param("from", from.toString())
                .param("to", from.plusDays(6).toString());
        for (int i = 0; i < extraParams.length; i += 2) {
            request.param(extraParams[i], extraParams[i + 1]);
        }
        return request;
    }

    private ResultActions export(String token, String report, String... extraParams) throws Exception {
        return mockMvc.perform(exportRequest(report, extraParams).headers(bearer(token)));
    }

    @Test
    @DisplayName("Cada reporte se descarga como un libro de Excel válido con su cabecera y su fila de total")
    void descargaCadaReporteComoExcel() throws Exception {
        String token = loginAsAdmin();
        for (String report : new String[]{"appointments", "no-show-rate", "occupancy"}) {
            MvcResult result = export(token, report, "format", "xlsx")
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", startsWith(XLSX)))
                    .andExpect(header().string("Content-Disposition", containsString(".xlsx")))
                    .andReturn();

            try (XSSFWorkbook workbook = new XSSFWorkbook(
                    new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
                Sheet sheet = workbook.getSheetAt(0);
                assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isNotBlank();
                assertThat(sheet.getRow(sheet.getLastRowNum()).getCell(0).getStringCellValue()).isEqualTo("Total");
            }
        }
    }

    @Test
    @DisplayName("El formato se reconoce sin distinguir mayúsculas y el filtro de especialidad es opcional")
    void aceptaElFormatoEnMayusculas() throws Exception {
        export(loginAsAdmin(), "appointments", "format", "XLSX")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith(XLSX)));
    }

    @Test
    @DisplayName("Sin indicar formato sigue descargando CSV, como antes")
    void sinFormatoDescargaCsv() throws Exception {
        export(loginAsAdmin(), "appointments")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString(".csv")));
    }

    @Test
    @DisplayName("Un formato desconocido responde 400 en el campo format")
    void formatoDesconocidoResponde400() throws Exception {
        export(loginAsAdmin(), "appointments", "format", "pdf")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATA"))
                .andExpect(jsonPath("$.fields[0].field").value("format"));
    }

    @Test
    @DisplayName("Una especialidad inexistente en el filtro responde 404")
    void especialidadInexistenteResponde404() throws Exception {
        export(loginAsAdmin(), "appointments", "format", "xlsx", "specialtyId", "999999")
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Solo el administrador puede exportar: sin token 401 y con rol de admisión 403")
    void soloElAdministradorPuedeExportar() throws Exception {
        mockMvc.perform(exportRequest("appointments", "format", "xlsx"))
                .andExpect(status().isUnauthorized());
        export(loginAsFrontDesk(), "appointments", "format", "xlsx")
                .andExpect(status().isForbidden());
    }
}
