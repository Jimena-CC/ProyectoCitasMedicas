package com.medicitas.api.schedule;

import com.medicitas.api.ApiIntegrationTestSupport;
import com.medicitas.api.catalog.LocationRepository;
import com.medicitas.api.catalog.Specialty;
import com.medicitas.api.catalog.SpecialtyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AvailabilityApiTest extends ApiIntegrationTestSupport {

    @Autowired
    private SpecialtyRepository specialtyRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Test
    void devuelveLosCatorceDiasConHorasEnFormatoHHmm() throws Exception {
        Specialty cardiologia = specialtyRepository.findByActiveTrueOrderByNameAsc().getFirst();
        Long sanIsidro = locationRepository.findByActiveTrueOrderByIdAsc().getFirst().getId();
        LocalDate from = LocalDate.now(clock).plusDays(1);

        mockMvc.perform(get("/api/v1/availability")
                        .param("specialtyId", cardiologia.getId().toString())
                        .param("locationId", sanIsidro.toString())
                        .param("from", from.toString())
                        .param("to", from.plusDays(13).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(14))
                .andExpect(jsonPath("$.days[0].date").value(from.toString()))
                .andExpect(jsonPath("$.days[*].slots[*].startTime", everyItem(matchesPattern("\\d{2}:\\d{2}"))));
    }

    @Test
    void rechazaRangosMayoresACatorceDias() throws Exception {
        LocalDate from = LocalDate.now(clock);
        mockMvc.perform(get("/api/v1/availability")
                        .param("specialtyId", "1")
                        .param("locationId", "1")
                        .param("from", from.toString())
                        .param("to", from.plusDays(14).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATA"))
                .andExpect(jsonPath("$.fields[0].field").value("to"));
    }
}
