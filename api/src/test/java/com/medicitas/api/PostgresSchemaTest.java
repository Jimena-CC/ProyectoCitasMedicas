package com.medicitas.api;

import com.medicitas.api.audit.AuditAction;
import com.medicitas.api.audit.AuditAction.AuditEntities;
import com.medicitas.api.audit.AuditLog;
import com.medicitas.api.audit.AuditLogRepository;
import com.medicitas.api.audit.AuditService;
import com.medicitas.api.catalog.LocationRepository;
import com.medicitas.api.demo.DemoDataLoader;
import com.medicitas.api.security.AppUser;
import com.medicitas.api.security.AppUserRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Levanta un PostgreSQL 15 real (sin Docker), ejecuta
 * docs/database/initial_schema.sql y arranca la API con el
 * perfil {@code postgres}: {@code ddl-auto=validate} falla si alguna entidad no
 * coincide con el script. Sobre ese mismo contexto se ejecutan las consultas que
 * se comportan distinto en PostgreSQL que en H2.
 */
@DisplayName("API sobre PostgreSQL")
class PostgresSchemaTest {

    private static final String JWT_SECRET = "clave-de-prueba-postgres-con-al-menos-treinta-y-dos-caracteres";

    private static EmbeddedPostgres postgres;
    private static ConfigurableApplicationContext context;

    @BeforeAll
    static void startApi() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
        // Flyway aplica V1__init_schema.sql automáticamente al levantar el contexto Spring
        context = new SpringApplicationBuilder(ApiApplication.class)
                .profiles("postgres")
                .run("--spring.datasource.url=" + postgres.getJdbcUrl("postgres", "postgres"),
                        "--spring.datasource.username=postgres",
                        "--spring.datasource.password=postgres",
                        "--server.port=0",
                        "--clinic.demo-data=true",
                        "--security.jwt.secret=" + JWT_SECRET,
                        "--messaging.job-interval-ms=3600000");
    }

    @AfterAll
    static void stopApi() throws Exception {
        if (context != null) {
            context.close();
        }
        if (postgres != null) {
            postgres.close();
        }
    }

    @Test
    @DisplayName("Las entidades coinciden con el script y los datos de demostración respetan sus restricciones")
    void lasEntidadesCoincidenConElScriptYLosDatosDemoRespetanSusRestricciones() {
        assertThat(context.getBean(LocationRepository.class).count()).isEqualTo(2);
    }

    @Test
    @DisplayName("La consulta paginada de auditoría se ejecuta en PostgreSQL con todos los filtros y sin ninguno")
    void laConsultaPaginadaDeAuditoriaFuncionaEnPostgres() {
        AuditService auditService = context.getBean(AuditService.class);
        AuditLogRepository auditLogRepository = context.getBean(AuditLogRepository.class);
        AppUser admin = context.getBean(AppUserRepository.class)
                .findByUsernameIgnoreCase(DemoDataLoader.ADMIN_USERNAME).orElseThrow();
        Instant now = Instant.now(context.getBean(Clock.class));
        AuditLog entry = auditService.record(admin, AuditAction.USER_LOGGED_IN, admin.getId(),
                "Inicio de sesión de prueba en PostgreSQL");

        Page<AuditLog> withAllFilters = auditLogRepository.search(AuditEntities.APP_USER,
                AuditAction.USER_LOGGED_IN.name(), admin.getId(), now.minusSeconds(60), now.plusSeconds(60),
                PageRequest.of(0, 10));
        Page<AuditLog> withoutFilters = auditLogRepository.search(null, null, null, null, null,
                PageRequest.of(0, 10));
        Page<AuditLog> otherUser = auditLogRepository.search(null, null, admin.getId() + 1_000, null, null,
                PageRequest.of(0, 10));

        assertAll(
                () -> assertThat(withAllFilters.getContent()).extracting(AuditLog::getId).contains(entry.getId()),
                () -> assertThat(withAllFilters.getContent())
                        .allSatisfy(log -> assertThat(log.getUser().getUsername()).isEqualTo("admin")),
                () -> assertThat(withoutFilters.getTotalElements()).isGreaterThanOrEqualTo(1),
                () -> assertThat(withoutFilters.getContent()).isSortedAccordingTo(
                        (a, b) -> b.getRegisteredAt().compareTo(a.getRegisteredAt())),
                () -> assertThat(otherUser.getContent()).isEmpty());
    }
}
