package com.medicitas.api.audit;

import com.medicitas.api.security.AppUser;
import com.medicitas.api.security.CurrentUserService;
import com.medicitas.api.security.SecurityProperties;
import com.medicitas.api.security.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditService")
class AuditServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private CurrentUserService currentUserService;

    private AuditService auditService;
    private AppUser frontDesk;

    @BeforeEach
    void preparar() {
        auditService = newAuditService(false);
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        frontDesk = new AppUser("admision", "$2a$10$hash", UserRole.FRONT_DESK, null);
        ReflectionTestUtils.setField(frontDesk, "id", 2L);
    }

    @AfterEach
    void limpiarPeticion() {
        RequestContextHolder.resetRequestAttributes();
    }

    private AuditService newAuditService(boolean trustForwardedFor) {
        SecurityProperties properties = new SecurityProperties(new SecurityProperties.Jwt("x".repeat(32), 480),
                new SecurityProperties.Cors(List.of()), new SecurityProperties.Audit(trustForwardedFor));
        return new AuditService(auditLogRepository, currentUserService, properties, CLOCK);
    }

    @Nested
    @DisplayName("Usuario responsable")
    class UsuarioResponsable {

        @Test
        @DisplayName("Fuera de una petición HTTP y sin sesión, el registro queda sin usuario ni IP")
        void sinSesionNiPeticionRegistraSinUsuarioNiIp() {
            when(currentUserService.current()).thenReturn(Optional.empty());

            AuditLog entry = auditService.record(AuditAction.APPOINTMENT_BOOKED, 52L, "Cita MCA-7K2Q9 reservada");

            assertAll(
                    () -> assertThat(entry.getUser()).isNull(),
                    () -> assertThat(entry.getSourceIp()).isNull(),
                    () -> assertThat(entry.getAction()).isEqualTo("APPOINTMENT_BOOKED"),
                    () -> assertThat(entry.getEntity()).isEqualTo("APPOINTMENT"),
                    () -> assertThat(entry.getEntityId()).isEqualTo(52L),
                    () -> assertThat(entry.getDetail()).isEqualTo("Cita MCA-7K2Q9 reservada"),
                    () -> assertThat(entry.getRegisteredAt()).isEqualTo(CLOCK.instant()));
        }

        @Test
        @DisplayName("Con sesión, el registro se atribuye al usuario autenticado")
        void conSesionRegistraAlUsuarioAutenticado() {
            when(currentUserService.current()).thenReturn(Optional.of(frontDesk));

            AuditLog entry = auditService.record(AuditAction.APPOINTMENT_CANCELLED, 52L, "Cita anulada");

            assertThat(entry.getUser()).isSameAs(frontDesk);
        }

        @Test
        @DisplayName("Un usuario explícito se registra sin consultar la sesión")
        void usuarioExplicitoNoConsultaLaSesion() {
            AuditLog entry = auditService.record(frontDesk, AuditAction.USER_LOGGED_IN, 2L, "Inicio de sesión");

            assertAll(
                    () -> assertThat(entry.getUser()).isSameAs(frontDesk),
                    () -> assertThat(entry.getEntity()).isEqualTo("APP_USER"));
        }
    }

    @Nested
    @DisplayName("IP de origen")
    class IpDeOrigen {

        @ParameterizedTest(name = "X-Forwarded-For=''{0}'' y remoto {1} registra {1}")
        @CsvSource(value = {
                "'', 192.168.1.20",
                "'10.0.0.5, 10.0.0.1', 192.168.1.20",
                "'1.2.3.4', 2001:db8::1"
        })
        @DisplayName("Por defecto ignora X-Forwarded-For: un cliente no puede falsificar su IP")
        void porDefectoTomaLaDireccionRemotaDeLaConexion(String forwardedFor, String remoteAddr) {
            prepareRequest(forwardedFor, remoteAddr);

            AuditLog entry = auditService.record(frontDesk, AuditAction.APPOINTMENT_BOOKED, 1L, "detalle");

            assertThat(entry.getSourceIp()).isEqualTo(remoteAddr);
        }

        @ParameterizedTest(name = "X-Forwarded-For=''{0}'' y remoto {1} registra {2}")
        @CsvSource(value = {
                "'', 192.168.1.20, 192.168.1.20",
                "'10.0.0.5, 10.0.0.1', 192.168.1.20, 10.0.0.5",
                "'2001:db8::1', 192.168.1.20, 2001:db8::1"
        })
        @DisplayName("Detrás de un proxy de confianza toma la primera IP de X-Forwarded-For")
        void conProxyDeConfianzaTomaLaPrimeraIpReenviada(String forwardedFor, String remoteAddr, String expected) {
            auditService = newAuditService(true);
            prepareRequest(forwardedFor, remoteAddr);

            AuditLog entry = auditService.record(frontDesk, AuditAction.APPOINTMENT_BOOKED, 1L, "detalle");

            assertThat(entry.getSourceIp()).isEqualTo(expected);
        }

        @Test
        @DisplayName("Una IP más larga que la columna se recorta a 45 caracteres")
        void recortaIpsDemasiadoLargas() {
            auditService = newAuditService(true);
            prepareRequest("x".repeat(60), "192.168.1.20");

            AuditLog entry = auditService.record(frontDesk, AuditAction.APPOINTMENT_BOOKED, 1L, "detalle");

            assertThat(entry.getSourceIp()).hasSize(45);
        }

        private void prepareRequest(String forwardedFor, String remoteAddr) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr(remoteAddr);
            if (!forwardedFor.isEmpty()) {
                request.addHeader(AuditService.FORWARDED_FOR_HEADER, forwardedFor);
            }
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        }
    }
}
