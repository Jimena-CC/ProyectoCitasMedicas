package com.medicitas.api.security;

import com.medicitas.api.catalog.Doctor;
import com.medicitas.api.catalog.Specialty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("AppUser y UserRole")
class AppUserTest {

    @Test
    @DisplayName("Un médico se muestra con su nombre completo y expone su doctorId")
    void elMedicoSeMuestraConSuNombreCompleto() {
        Specialty cardiologia = new Specialty("Cardiología", null, (short) 20);
        Doctor doctor = new Doctor("045712", null, "Carla", "Benavides Ortiz", cardiologia);
        ReflectionTestUtils.setField(doctor, "id", 7L);

        AppUser user = new AppUser("dr.benavides", "$2a$10$hash", UserRole.DOCTOR, doctor);

        assertAll(
                () -> assertThat(user.getDisplayName()).isEqualTo("Carla Benavides Ortiz"),
                () -> assertThat(user.getDoctorId()).isEqualTo(7L),
                () -> assertThat(user.isActive()).isTrue(),
                () -> assertThat(user.getLastAccess()).isNull());
    }

    @Test
    @DisplayName("Un usuario sin médico se muestra con su nombre de usuario")
    void elUsuarioSinMedicoSeMuestraConSuUsername() {
        AppUser user = new AppUser("admision", "$2a$10$hash", UserRole.FRONT_DESK, null);
        Instant now = Instant.parse("2026-09-13T15:00:00Z");

        user.touchLastAccess(now);
        user.setActive(false);
        user.changePassword("$2a$10$otro");

        assertAll(
                () -> assertThat(user.getDisplayName()).isEqualTo("admision"),
                () -> assertThat(user.getDoctorId()).isNull(),
                () -> assertThat(user.getLastAccess()).isEqualTo(now),
                () -> assertThat(user.isActive()).isFalse(),
                () -> assertThat(user.getPasswordHash()).isEqualTo("$2a$10$otro"));
    }

    @ParameterizedTest(name = "{0} -> {1} / {2}")
    @CsvSource({
            "ADMIN, ROLE_ADMIN, Administrador",
            "FRONT_DESK, ROLE_FRONT_DESK, Admisión",
            "DOCTOR, ROLE_DOCTOR, Médico"
    })
    @DisplayName("Cada perfil tiene su autoridad ROLE_<perfil> y una etiqueta en español")
    void cadaPerfilTieneSuAutoridadYEtiqueta(UserRole role, String authority, String label) {
        assertAll(
                () -> assertThat(role.authority()).isEqualTo(authority),
                () -> assertThat(role.label()).isEqualTo(label));
    }
}
