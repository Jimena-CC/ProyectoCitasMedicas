package com.medicitas.api.admin;

import com.medicitas.api.ApiIntegrationTestSupport;
import com.medicitas.api.audit.AuditAction;
import com.medicitas.api.audit.AuditAction.AuditEntities;
import com.medicitas.api.audit.AuditLog;
import com.medicitas.api.audit.AuditLogRepository;
import com.medicitas.api.catalog.Doctor;
import com.medicitas.api.catalog.DoctorRepository;
import com.medicitas.api.catalog.SpecialtyRepository;
import com.medicitas.api.demo.DemoDataLoader;
import com.medicitas.api.security.AppUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gestión de usuarios del personal por el administrador: alta, edición, contraseña, reglas y auditoría.
 * {@code SecurityAccessTest} ya cubre el 401 sin token y el 403 por rol en {@code GET /admin/users};
 * aquí se complementa con las operaciones de escritura.
 */
@DisplayName("Administración de usuarios")
class AdminUsersApiTest extends ApiIntegrationTestSupport {

    private static final String USERS = "/api/v1/admin/users";
    private static final String PASSWORD = "clave.segura.123";

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private SpecialtyRepository specialtyRepository;

    private static String uniqueUsername(String prefix) {
        return prefix + "." + UUID.randomUUID().toString().substring(0, 8);
    }

    private ResultActions createUser(String token, String body) throws Exception {
        return mockMvc.perform(post(USERS).headers(bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** Crea un usuario de admisión activo y devuelve su id. */
    private long createFrontDeskUser(String token, String username) throws Exception {
        MvcResult result = createUser(token, """
                {"username":"%s","password":"%s","role":"FRONT_DESK"}""".formatted(username, PASSWORD))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    private ResultActions updateUser(String token, long id, String body) throws Exception {
        return mockMvc.perform(put(USERS + "/" + id).headers(bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","password":"%s"}""".formatted(username, password)));
    }

    private Doctor createUnlinkedDoctor() {
        return doctorRepository.save(new Doctor("T" + UUID.randomUUID().toString().substring(0, 8), null,
                "Prueba", "Sin Usuario", specialtyRepository.findByActiveTrueOrderByNameAsc().getFirst()));
    }

    @Nested
    @DisplayName("Alta")
    class Alta {

        @Test
        @DisplayName("Crea un usuario de admisión, no devuelve la contraseña y el usuario puede iniciar sesión")
        void creaUsuarioDeAdmisionSinDevolverLaContrasena() throws Exception {
            String token = loginAsAdmin();
            String username = uniqueUsername("admision");

            MvcResult result = createUser(token, """
                    {"username":"%s","password":"%s","role":"FRONT_DESK"}""".formatted(username, PASSWORD))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", startsWith(USERS + "/")))
                    .andExpect(jsonPath("$.username").value(username))
                    .andExpect(jsonPath("$.role").value("FRONT_DESK"))
                    .andExpect(jsonPath("$.fullName").value(username))
                    .andExpect(jsonPath("$.doctorId").isEmpty())
                    .andExpect(jsonPath("$.active").value(true))
                    .andExpect(jsonPath("$.lastAccess").isEmpty())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist())
                    .andExpect(jsonPath("$.password").doesNotExist())
                    .andReturn();
            long id = readId(result);

            login(username, PASSWORD).andExpect(status().isOk());
            // El alta y, después, el inicio de sesión del nuevo usuario.
            List<AuditLog> entries = auditLogRepository.findByEntityAndEntityIdOrderByIdAsc(AuditEntities.APP_USER, id);
            assertThat(entries).extracting(AuditLog::getAction)
                    .containsExactly(AuditAction.USER_CREATED.name(), AuditAction.USER_LOGGED_IN.name());
            assertAll(
                    () -> assertThat(entries.getFirst().getUser().getUsername()).isEqualTo(DemoDataLoader.ADMIN_USERNAME),
                    () -> assertThat(entries.getFirst().getDetail()).contains(username).contains("Admisión")
                            .doesNotContain(PASSWORD));
        }

        @Test
        @DisplayName("Un médico queda vinculado a su ficha y muestra su nombre completo")
        void creaUsuarioMedicoVinculadoASuFicha() throws Exception {
            String token = loginAsAdmin();
            Doctor doctor = createUnlinkedDoctor();
            String username = uniqueUsername("dr.prueba");

            createUser(token, """
                    {"username":"%s","password":"%s","role":"DOCTOR","doctorId":%d}"""
                    .formatted(username, PASSWORD, doctor.getId()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.role").value("DOCTOR"))
                    .andExpect(jsonPath("$.doctorId").value(doctor.getId()))
                    .andExpect(jsonPath("$.fullName").value("Prueba Sin Usuario"));
        }

        @Test
        @DisplayName("Un nombre de usuario repetido, aunque cambie de mayúsculas, responde 409 USERNAME_TAKEN")
        void nombreRepetidoResponde409() throws Exception {
            String token = loginAsAdmin();
            String username = uniqueUsername("Repetido");
            createFrontDeskUser(token, username);

            createUser(token, """
                    {"username":"%s","password":"%s","role":"FRONT_DESK"}"""
                    .formatted(username.toUpperCase(), PASSWORD))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
        }

        @Test
        @DisplayName("Un médico sin doctorId responde 400 INVALID_DATA en el campo doctorId")
        void medicoSinDoctorIdResponde400() throws Exception {
            createUser(loginAsAdmin(), """
                    {"username":"%s","password":"%s","role":"DOCTOR"}""".formatted(uniqueUsername("dr.x"), PASSWORD))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_DATA"))
                    .andExpect(jsonPath("$.fields[0].field").value("doctorId"));
        }

        @Test
        @DisplayName("Un médico que ya tiene usuario responde 409 DOCTOR_ALREADY_LINKED")
        void medicoYaVinculadoResponde409() throws Exception {
            Long linkedDoctorId = appUserRepository
                    .findByUsernameIgnoreCase(DemoDataLoader.DOCTOR_USERNAME_PREFIX + "benavides")
                    .orElseThrow().getDoctorId();

            createUser(loginAsAdmin(), """
                    {"username":"%s","password":"%s","role":"DOCTOR","doctorId":%d}"""
                    .formatted(uniqueUsername("dr.otro"), PASSWORD, linkedDoctorId))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DOCTOR_ALREADY_LINKED"));
        }

        @Test
        @DisplayName("Un doctorId inexistente responde 404 RESOURCE_NOT_FOUND")
        void medicoInexistenteResponde404() throws Exception {
            createUser(loginAsAdmin(), """
                    {"username":"%s","password":"%s","role":"DOCTOR","doctorId":999999}"""
                    .formatted(uniqueUsername("dr.nadie"), PASSWORD))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }

        @Test
        @DisplayName("Un perfil distinto de médico no admite doctorId")
        void perfilNoMedicoConDoctorIdResponde400() throws Exception {
            createUser(loginAsAdmin(), """
                    {"username":"%s","password":"%s","role":"ADMIN","doctorId":1}"""
                    .formatted(uniqueUsername("admin.x"), PASSWORD))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields[0].field").value("doctorId"));
        }

        @ParameterizedTest(name = "contraseña \"{0}\"")
        @ValueSource(strings = {"", "corta", "1234567"})
        @DisplayName("Una contraseña de menos de 8 caracteres responde 400 INVALID_DATA")
        void contrasenaCortaResponde400(String password) throws Exception {
            createUser(loginAsAdmin(), """
                    {"username":"%s","password":"%s","role":"FRONT_DESK"}""".formatted(uniqueUsername("corta"), password))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_DATA"))
                    .andExpect(jsonPath("$.fields[*].field").value(hasItem("password")));
        }
    }

    @Nested
    @DisplayName("Consulta y edición")
    class ConsultaYEdicion {

        @Test
        @DisplayName("GET /admin/users/{id} devuelve el usuario y 404 si no existe")
        void consultaPorId() throws Exception {
            String token = loginAsAdmin();
            String username = uniqueUsername("consulta");
            long id = createFrontDeskUser(token, username);

            mockMvc.perform(get(USERS + "/" + id).headers(bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.username").value(username));
            mockMvc.perform(get(USERS + "/999999").headers(bearer(token)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }

        @Test
        @DisplayName("Cambiar el perfil y desactivar quedan auditados y el usuario desactivado ya no entra")
        void cambiaElPerfilYDesactiva() throws Exception {
            String token = loginAsAdmin();
            String username = uniqueUsername("editable");
            long id = createFrontDeskUser(token, username);

            updateUser(token, id, """
                    {"role":"ADMIN","active":true}""")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("ADMIN"))
                    .andExpect(jsonPath("$.active").value(true));
            updateUser(token, id, """
                    {"role":"ADMIN","active":false}""")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(false));

            login(username, PASSWORD)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("USER_INACTIVE"));
            List<AuditLog> entries = auditLogRepository.findByEntityAndEntityIdOrderByIdAsc(AuditEntities.APP_USER, id);
            assertThat(entries).extracting(AuditLog::getAction).containsExactly(
                    AuditAction.USER_CREATED.name(), AuditAction.USER_UPDATED.name(), AuditAction.USER_DEACTIVATED.name());
        }

        @Test
        @DisplayName("Al editar, el perfil de médico exige un médico libre y los demás perfiles lo desvinculan")
        void editarCambiaElMedicoVinculado() throws Exception {
            String token = loginAsAdmin();
            long id = createFrontDeskUser(token, uniqueUsername("cambia.medico"));
            Doctor doctor = createUnlinkedDoctor();

            updateUser(token, id, """
                    {"role":"DOCTOR","doctorId":%d,"active":true}""".formatted(doctor.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("DOCTOR"))
                    .andExpect(jsonPath("$.doctorId").value(doctor.getId()));
            updateUser(token, id, """
                    {"role":"FRONT_DESK","active":true}""")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.doctorId").isEmpty());
            updateUser(token, id, """
                    {"role":"DOCTOR","active":true}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields[0].field").value("doctorId"));
        }

        @Test
        @DisplayName("Un administrador no puede desactivarse ni quitarse el perfil de administrador (422)")
        void elAdminNoPuedeBloquearseASiMismo() throws Exception {
            String token = loginAsAdmin();
            MvcResult me = mockMvc.perform(get("/api/v1/auth/me").headers(bearer(token))).andReturn();
            long adminId = readId(me);

            updateUser(token, adminId, """
                    {"role":"ADMIN","active":false}""")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("SELF_DEACTIVATION"))
                    .andExpect(jsonPath("$.message").value(
                            "No puedes desactivar tu propio usuario. Pídeselo a otro administrador."));
            updateUser(token, adminId, """
                    {"role":"FRONT_DESK","active":true}""")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("SELF_DEACTIVATION"));
            mockMvc.perform(get(USERS + "/" + adminId).headers(bearer(token)))
                    .andExpect(jsonPath("$.role").value("ADMIN"))
                    .andExpect(jsonPath("$.active").value(true));
        }

        @Test
        @DisplayName("Restablecer la contraseña responde 204, invalida la anterior y no la guarda en la auditoría")
        void restableceLaContrasena() throws Exception {
            String token = loginAsAdmin();
            String username = uniqueUsername("clave");
            long id = createFrontDeskUser(token, username);
            String newPassword = "otra.clave.456";

            mockMvc.perform(put(USERS + "/" + id + "/password").headers(bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"password":"%s"}""".formatted(newPassword)))
                    .andExpect(status().isNoContent());

            login(username, PASSWORD).andExpect(status().isUnauthorized());
            login(username, newPassword).andExpect(status().isOk());
            List<AuditLog> changes = auditLogRepository.findByEntityAndEntityIdOrderByIdAsc(AuditEntities.APP_USER, id)
                    .stream().filter(entry -> entry.getAction().equals(AuditAction.USER_PASSWORD_CHANGED.name())).toList();
            assertThat(changes).hasSize(1);
            assertThat(changes.getFirst().getDetail()).contains(username).doesNotContain(newPassword);
        }

        @Test
        @DisplayName("Una contraseña corta al restablecer responde 400")
        void contrasenaCortaAlRestablecerResponde400() throws Exception {
            String token = loginAsAdmin();
            long id = createFrontDeskUser(token, uniqueUsername("clave.corta"));

            mockMvc.perform(put(USERS + "/" + id + "/password").headers(bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"password":"abc"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_DATA"));
        }
    }

    @Nested
    @DisplayName("Acceso")
    class Acceso {

        @Test
        @DisplayName("Admisión no puede crear usuarios: 403 FORBIDDEN")
        void admisionNoPuedeCrearUsuarios() throws Exception {
            createUser(loginAsFrontDesk(), """
                    {"username":"%s","password":"%s","role":"FRONT_DESK"}""".formatted(uniqueUsername("x"), PASSWORD))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("Un médico no puede editar usuarios: 403 FORBIDDEN")
        void medicoNoPuedeEditarUsuarios() throws Exception {
            String token = loginAs(DemoDataLoader.DOCTOR_USERNAME_PREFIX + "benavides", DemoDataLoader.DOCTOR_PASSWORD);

            updateUser(token, 1, """
                    {"role":"ADMIN","active":true}""")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("Sin token, restablecer una contraseña responde 401")
        void sinTokenResponde401() throws Exception {
            mockMvc.perform(put(USERS + "/1/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"password":"%s"}""".formatted(PASSWORD)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }
}
