package com.medicitas.api.security;

import com.medicitas.api.audit.AuditAction;
import com.medicitas.api.audit.AuditService;
import com.medicitas.api.catalog.Doctor;
import com.medicitas.api.catalog.Specialty;
import com.medicitas.api.common.BusinessException;
import com.medicitas.api.common.ErrorCodes;
import com.medicitas.api.security.AuthDtos.LoginRequest;
import com.medicitas.api.security.AuthDtos.LoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));
    private static final String SECRET = "clave-de-prueba-con-al-menos-treinta-y-dos-caracteres";
    private static final SecretKey KEY = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    /** BCrypt con coste bajo para que la prueba sea rápida; en la aplicación se usa el coste por defecto. */
    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private AuditService auditService;

    private AuthService authService;
    private JwtDecoder jwtDecoder;
    private AppUser doctorUser;

    @BeforeEach
    void preparar() {
        SecurityProperties properties = new SecurityProperties(new SecurityProperties.Jwt(SECRET, 480),
                new SecurityProperties.Cors(List.of("http://localhost:5173")));
        authService = new AuthService(appUserRepository, ENCODER,
                NimbusJwtEncoder.withSecretKey(KEY).algorithm(MacAlgorithm.HS256).build(), properties, auditService,
                CLOCK);
        // El reloj de la prueba está fijo en 2026-09-13: se omite la validación de vigencia al decodificar.
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(token -> OAuth2TokenValidatorResult.success());
        jwtDecoder = decoder;

        Specialty cardiologia = new Specialty("Cardiología", null, (short) 20);
        Doctor doctor = new Doctor("045712", null, "Carla", "Benavides Ortiz", cardiologia);
        ReflectionTestUtils.setField(doctor, "id", 7L);
        doctorUser = new AppUser("dr.benavides", ENCODER.encode("doctor123"), UserRole.DOCTOR, doctor);
        ReflectionTestUtils.setField(doctorUser, "id", 3L);
    }

    @Test
    @DisplayName("Credenciales correctas emiten un JWT con sub, role y uid, actualizan el último acceso y auditan")
    void credencialesCorrectasEmitenElToken() {
        when(appUserRepository.findByUsernameIgnoreCase("dr.benavides")).thenReturn(Optional.of(doctorUser));

        LoginResponse response = authService.login(new LoginRequest(" dr.benavides ", "doctor123"));

        Jwt jwt = jwtDecoder.decode(response.token());
        assertAll(
                () -> assertThat(jwt.getSubject()).isEqualTo("dr.benavides"),
                () -> assertThat(jwt.getClaimAsString(JwtRoleAuthenticationConverter.ROLE_CLAIM)).isEqualTo("DOCTOR"),
                () -> assertThat(jwt.<Number>getClaim(JwtRoleAuthenticationConverter.USER_ID_CLAIM).longValue())
                        .isEqualTo(3L),
                () -> assertThat(jwt.getClaimAsString("iss")).isEqualTo(AuthService.ISSUER),
                () -> assertThat(jwt.getExpiresAt()).isEqualTo(CLOCK.instant().plusSeconds(480 * 60)),
                () -> assertThat(response.expiresAt().toInstant()).isEqualTo(CLOCK.instant().plusSeconds(480 * 60)),
                () -> assertThat(response.expiresAt().getOffset().getId()).isEqualTo("-05:00"),
                () -> assertThat(response.user().username()).isEqualTo("dr.benavides"),
                () -> assertThat(response.user().role()).isEqualTo(UserRole.DOCTOR),
                () -> assertThat(response.user().fullName()).isEqualTo("Carla Benavides Ortiz"),
                () -> assertThat(response.user().doctorId()).isEqualTo(7L),
                () -> assertThat(doctorUser.getLastAccess()).isEqualTo(CLOCK.instant()));
        verify(auditService).record(eq(doctorUser), eq(AuditAction.USER_LOGGED_IN), eq(3L), anyString());
    }

    @ParameterizedTest(name = "usuario ''{0}'' con contraseña ''{1}''")
    @CsvSource({
            "dr.benavides, incorrecta",
            "nadie, doctor123"
    })
    @DisplayName("Usuario o contraseña incorrectos responden 401 INVALID_CREDENTIALS sin distinguir el caso")
    void credencialesIncorrectasResponden401(String username, String password) {
        when(appUserRepository.findByUsernameIgnoreCase(username))
                .thenReturn(username.equals("dr.benavides") ? Optional.of(doctorUser) : Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest(username, password)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertAll(
                        () -> assertThat(ex.getCode()).isEqualTo(ErrorCodes.INVALID_CREDENTIALS),
                        () -> assertThat(ex.getStatus().value()).isEqualTo(401),
                        () -> assertThat(ex.getMessage()).isEqualTo("Usuario o contraseña incorrectos.")));
        assertThat(doctorUser.getLastAccess()).isNull();
        verify(auditService, never()).record(eq(doctorUser), eq(AuditAction.USER_LOGGED_IN), anyLong(), anyString());
    }

    @Test
    @DisplayName("Un usuario desactivado no recibe token aunque su contraseña sea correcta")
    void usuarioInactivoResponde403() {
        doctorUser.setActive(false);
        when(appUserRepository.findByUsernameIgnoreCase("dr.benavides")).thenReturn(Optional.of(doctorUser));

        assertThatThrownBy(() -> authService.login(new LoginRequest("dr.benavides", "doctor123")))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertAll(
                        () -> assertThat(ex.getCode()).isEqualTo(ErrorCodes.USER_INACTIVE),
                        () -> assertThat(ex.getStatus().value()).isEqualTo(403)));
        assertThat(doctorUser.getLastAccess()).isNull();
    }
}
