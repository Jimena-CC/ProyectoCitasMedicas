package com.medicitas.api;

import com.medicitas.api.catalog.LocationRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.file.Path;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Levanta un PostgreSQL 15 real (sin Docker), ejecuta docs/database/initial_schema.sql y arranca la API con el
 * perfil {@code postgres}: {@code ddl-auto=validate} falla si alguna entidad no coincide con el script.
 */
class PostgresSchemaTest {

    private static final Path SCRIPT = Path.of("..", "docs", "database", "initial_schema.sql");

    private static EmbeddedPostgres postgres;

    @BeforeAll
    static void startPostgres() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
        try (Connection connection = postgres.getPostgresDatabase().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(SCRIPT));
        }
    }

    @AfterAll
    static void stopPostgres() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @Test
    void lasEntidadesCoincidenConElScriptYLosDatosDemoRespetanSusRestricciones() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(ApiApplication.class)
                .profiles("postgres")
                .run("--spring.datasource.url=" + postgres.getJdbcUrl("postgres", "postgres"),
                        "--spring.datasource.username=postgres",
                        "--spring.datasource.password=postgres",
                        "--server.port=0",
                        "--clinic.demo-data=true",
                        "--messaging.job-interval-ms=3600000")) {
            assertThat(context.getBean(LocationRepository.class).count()).isEqualTo(2);
        }
    }
}
