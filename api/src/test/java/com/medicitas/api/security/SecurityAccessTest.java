package com.medicitas.api.security;

import com.medicitas.api.ApiIntegrationTestSupport;
import com.medicitas.api.demo.DemoDataLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reglas de acceso de la API: kiosko público, login y rutas del personal por rol.
 */
@DisplayName("Acceso por rol")
class SecurityAccessTest extends ApiIntegrationTestSupport {

    private static final String DOCTOR_USERNAME = DemoDataLoader.DOCTOR_USERNAME_PREFIX + "benavides";

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    /** Inicia sesión con el usuario de demostración del rol indicado. */
    private String loginAsRole(UserRole role) throws Exception {
        return switch (role) {
            case ADMIN -> loginAsAdmin();
            case FRONT_DESK -> loginAsFrontDesk();
            case DOCTOR -> loginAs(DOCTOR_USERNAME, DemoDataLoader.DOCTOR_PASSWORD);
        };
    }

    /** Firma con la clave de la API un token con el emisor, usuario y rol indicados, vigente 10 minutos. */
    private String issueToken(String issuer, long userId, UserRole role) {
        Instant now = Instant.now(clock);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .subject("usuario")
                .claim(JwtRoleAuthenticationConverter.ROLE_CLAIM, role.name())
                .claim(JwtRoleAuthenticationConverter.USER_ID_CLAIM, userId)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    /** Crea un usuario activo de admisión con el nombre indicado. */
    private AppUser createFrontDeskUser(String username, String password) {
        return appUserRepository.save(new AppUser(username, passwordEncoder.encode(password), UserRole.FRONT_DESK, null));
    }

    @Nested
    @DisplayName("Sin sesión")
    class SinSesion {

        @Test
        @DisplayName("GET /auth/me responde 401 UNAUTHORIZED con mensaje en español")
        void meSinTokenResponde401() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value("Debes iniciar sesión para continuar."))
                    .andExpect(jsonPath("$.timestamp", matchesPattern(".+-05:00")));
        }

        @Test
        @DisplayName("Las rutas de administración responden 401 sin token")
        void adminSinTokenResponde401() throws Exception {
            mockMvc.perform(get("/api/v1/admin/users"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("Un token manipulado responde 401 con un mensaje de sesión no válida")
        void tokenInvalidoResponde401() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me").headers(bearer("abc.def.ghi")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value("Tu sesión no es válida o ya venció. Vuelve a iniciar sesión."));
        }

        @Test
        @DisplayName("Un token firmado con la clave de la API pero con otro emisor responde 401")
        void tokenDeOtroEmisorResponde401() throws Exception {
            AppUser admin = appUserRepository.findByUsernameIgnoreCase(DemoDataLoader.ADMIN_USERNAME).orElseThrow();
            String foreignToken = issueToken("otro-sistema", admin.getId(), UserRole.ADMIN);

            mockMvc.perform(get("/api/v1/admin/users").headers(bearer(foreignToken)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Nested
    @DisplayName("Inicio de sesión")
    class InicioDeSesion {

        @Test
        @DisplayName("Credenciales correctas devuelven token, vencimiento y datos del usuario")
        void loginCorrectoDevuelveToken() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"admin","password":"admin123"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token", matchesPattern("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")))
                    .andExpect(jsonPath("$.expiresAt", matchesPattern(".+-05:00")))
                    .andExpect(jsonPath("$.user.username").value("admin"))
                    .andExpect(jsonPath("$.user.role").value("ADMIN"))
                    .andExpect(jsonPath("$.user.fullName").value("admin"))
                    .andExpect(jsonPath("$.user.doctorId").isEmpty());
        }

        @Test
        @DisplayName("El usuario médico queda vinculado a su ficha de doctor")
        void loginDeMedicoIncluyeSuDoctorId() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"%s","password":"%s"}"""
                                    .formatted(DOCTOR_USERNAME, DemoDataLoader.DOCTOR_PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.role").value("DOCTOR"))
                    .andExpect(jsonPath("$.user.fullName").value("Carla Benavides Ortiz"))
                    .andExpect(jsonPath("$.user.doctorId").isNumber());
        }

        @Test
        @DisplayName("Contraseña incorrecta responde 401 sin revelar qué dato falló")
        void loginIncorrectoResponde401() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"admin","password":"otra"}"""))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.message").value("Usuario o contraseña incorrectos."));
        }

        @Test
        @DisplayName("Usuario inexistente responde el mismo 401 que una contraseña incorrecta")
        void usuarioInexistenteResponde401() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"nadie","password":"admin123"}"""))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        @Test
        @DisplayName("Usuario desactivado responde 403 USER_INACTIVE")
        void usuarioInactivoResponde403() throws Exception {
            AppUser inactive = new AppUser("inactivo.prueba", passwordEncoder.encode("clave123"),
                    UserRole.FRONT_DESK, null);
            inactive.setActive(false);
            appUserRepository.save(inactive);

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"inactivo.prueba","password":"clave123"}"""))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("USER_INACTIVE"));
        }

        @Test
        @DisplayName("Sin usuario ni contraseña responde 400 INVALID_DATA")
        void loginSinDatosResponde400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_DATA"));
        }
    }

    @Nested
    @DisplayName("Con sesión")
    class ConSesion {

        @Test
        @DisplayName("GET /auth/me devuelve el usuario del token")
        void meConTokenDevuelveElUsuario() throws Exception {
            String token = loginAsFrontDesk();

            mockMvc.perform(get("/api/v1/auth/me").headers(bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("admision"))
                    .andExpect(jsonPath("$.role").value("FRONT_DESK"));
        }

        @Test
        @DisplayName("Un médico en /admin/** recibe 403 FORBIDDEN")
        void medicoEnAdminResponde403() throws Exception {
            String token = loginAs(DOCTOR_USERNAME, DemoDataLoader.DOCTOR_PASSWORD);

            mockMvc.perform(get("/api/v1/admin/users").headers(bearer(token)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.message").value("No tienes permiso para realizar esta acción."));
        }

        /**
         * Matriz rol/ruta de {@code SecurityConfig}. Las rutas {@code /doctor/**} y {@code /front-desk/**} aún no
         * tienen controlador: cuando el rol pasa la autorización, la API responde {@code 404 RESOURCE_NOT_FOUND}
         * en lugar de {@code 403}, lo que demuestra que la regla dejó pasar la petición.
         */
        @ParameterizedTest(name = "{0} en {1} responde {2} {3}")
        @CsvSource({
                "DOCTOR,     /api/v1/doctor/agenda,    404, RESOURCE_NOT_FOUND",
                "FRONT_DESK, /api/v1/doctor/agenda,    403, FORBIDDEN",
                "ADMIN,      /api/v1/doctor/agenda,    403, FORBIDDEN",
                "FRONT_DESK, /api/v1/front-desk/ping,  404, RESOURCE_NOT_FOUND",
                "ADMIN,      /api/v1/front-desk/ping,  404, RESOURCE_NOT_FOUND",
                "DOCTOR,     /api/v1/front-desk/ping,  403, FORBIDDEN",
                "ADMIN,      /api/v1/admin/users,      200, ",
                "FRONT_DESK, /api/v1/admin/users,      403, FORBIDDEN",
                "DOCTOR,     /api/v1/admin/users,      403, FORBIDDEN"
        })
        @DisplayName("Cada rol solo pasa la autorización de las rutas que le corresponden")
        void cadaRolSoloAccedeASusRutas(UserRole role, String path, int expectedStatus, String expectedCode)
                throws Exception {
            String token = loginAsRole(role);

            var result = mockMvc.perform(get(path).headers(bearer(token)))
                    .andExpect(status().is(expectedStatus));
            if (expectedCode != null) {
                result.andExpect(jsonPath("$.code").value(expectedCode));
            }
        }

        @Test
        @DisplayName("Un usuario desactivado después de iniciar sesión recibe 403 USER_INACTIVE aunque su token siga vigente")
        void usuarioDesactivadoTrasElLoginPierdeElAccesoDeInmediato() throws Exception {
            String username = "admision.temporal." + UUID.randomUUID().toString().substring(0, 8);
            AppUser user = createFrontDeskUser(username, "clave123");
            String token = loginAs(username, "clave123");
            mockMvc.perform(get("/api/v1/auth/me").headers(bearer(token))).andExpect(status().isOk());

            user.setActive(false);
            appUserRepository.save(user);

            mockMvc.perform(get("/api/v1/front-desk/ping").headers(bearer(token)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("USER_INACTIVE"))
                    .andExpect(jsonPath("$.message").value(
                            "Tu usuario está desactivado. Comunícate con el administrador del sistema."));
            mockMvc.perform(get("/api/v1/auth/me").headers(bearer(token)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("USER_INACTIVE"));
            // Tampoco puede operar como admisión en las rutas del kiosko con ese token.
            mockMvc.perform(get("/api/v1/locations").headers(bearer(token)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("USER_INACTIVE"));
        }

        @Test
        @DisplayName("Un cambio de rol surte efecto de inmediato: la autoridad sale de la base y no del claim del token")
        void unCambioDeRolAplicaDeInmediatoConElMismoToken() throws Exception {
            String username = "admision.ascenso." + UUID.randomUUID().toString().substring(0, 8);
            AppUser user = createFrontDeskUser(username, "clave123");
            String token = loginAs(username, "clave123");
            mockMvc.perform(get("/api/v1/admin/users").headers(bearer(token))).andExpect(status().isForbidden());

            user.changeRole(UserRole.ADMIN);
            appUserRepository.save(user);

            mockMvc.perform(get("/api/v1/admin/users").headers(bearer(token))).andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/auth/me").headers(bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("ADMIN"));
        }

        @Test
        @DisplayName("Un token bien firmado cuyo usuario no existe responde 401")
        void tokenDeUsuarioInexistenteResponde401() throws Exception {
            String token = issueToken(AuthService.ISSUER, 999_999L, UserRole.ADMIN);

            mockMvc.perform(get("/api/v1/auth/me").headers(bearer(token)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value("Tu sesión no es válida o ya venció. Vuelve a iniciar sesión."));
        }

        @Test
        @DisplayName("El administrador lista los usuarios del personal")
        void adminListaLosUsuarios() throws Exception {
            String token = loginAsAdmin();

            mockMvc.perform(get("/api/v1/admin/users").headers(bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].username", hasItem("admin")))
                    .andExpect(jsonPath("$[*].username", hasItem("admision")))
                    .andExpect(jsonPath("$[*].username", hasItem(DOCTOR_USERNAME)))
                    .andExpect(jsonPath("$[?(@.username == 'admin')].lastAccess", hasItem(matchesPattern(".+-05:00"))))
                    .andExpect(jsonPath("$[?(@.username == '" + DOCTOR_USERNAME + "')].doctorId").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("Kiosko público")
    class KioskoPublico {

        @ParameterizedTest(name = "GET {0} responde 200 sin token")
        @ValueSource(strings = {
                "/api/v1/locations",
                "/api/v1/insurers",
                "/api/v1/patients?documentType=DNI&documentNumber=45871236",
                "/actuator/health",
                "/v3/api-docs"})
        void lasRutasDelKioskoSiguenPublicas(String path) throws Exception {
            mockMvc.perform(get(path)).andExpect(status().isOk());
        }

        @Test
        @DisplayName("La especificación OpenAPI declara el esquema Bearer")
        void openApiDeclaraElEsquemaBearer() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                    .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists());
        }
    }
}
