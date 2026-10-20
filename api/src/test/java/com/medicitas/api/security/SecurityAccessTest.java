package com.medicitas.api.security;

import com.medicitas.api.ApiIntegrationTestSupport;
import com.medicitas.api.demo.DemoDataLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

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

        @Test
        @DisplayName("Admisión en /doctor/** recibe 403 FORBIDDEN")
        void admisionEnDoctorResponde403() throws Exception {
            String token = loginAsFrontDesk();

            mockMvc.perform(get("/api/v1/doctor/agenda").headers(bearer(token)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
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
