package com.medicitas.api;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiApplicationTests extends ApiIntegrationTestSupport {

	@Test
	void contextLoads() {
	}

	@Test
	void publicaLaEspecificacionOpenApi() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/v1/appointments']").exists());
	}

	@Test
	void listaLasDosSedesDeLaClinica() throws Exception {
		mockMvc.perform(get("/api/v1/locations"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].code").value("SI"))
				.andExpect(jsonPath("$[1].code").value("LM"));
	}
}
