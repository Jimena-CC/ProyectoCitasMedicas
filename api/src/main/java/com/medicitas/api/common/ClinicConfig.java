package com.medicitas.api.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableAsync
@EnableScheduling
public class ClinicConfig {

    /**
     * Reloj con la zona horaria de la clínica: define "hoy", la disponibilidad y los recordatorios.
     */
    @Bean
    public Clock clinicClock(@Value("${clinic.time-zone:America/Lima}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }

    @Bean
    public OpenAPI medicitasOpenApi() {
        return new OpenAPI().info(new Info()
                .title("API MediCitas Anglo")
                .version("v1")
                .description("Reserva, reprogramación y anulación de citas desde el Kiosko de Autoatención "
                        + "de la Clínica Anglo Americana (sedes San Isidro y La Molina)."));
    }
}
