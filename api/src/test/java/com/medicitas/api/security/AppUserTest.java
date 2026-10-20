package com.medicitas.api.security;

import com.medicitas.api.catalog.Doctor;
import com.medicitas.api.catalog.Specialty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
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

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "ADMIN, ROLE_ADMIN",
            "FRONT_DESK, ROLE_FRONT_DESK",
            "DOCTOR, ROLE_DOCTOR"
    })
    @DisplayName("Cada perfil se traduce a su autoridad ROLE_<perfil>, también desde el claim del JWT")
    void cadaPerfilTieneSuAutoridad(UserRole role, String authority) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("usuario")
                .claim(JwtRoleAuthenticationConverter.ROLE_CLAIM, role.name())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        assertAll(
                () -> assertThat(role.authority()).isEqualTo(authority),
                () -> assertThat(new JwtRoleAuthenticationConverter().convert(jwt).getAuthorities())
                        .containsExactly(new SimpleGrantedAuthority(authority)));
    }

    @Test
    @DisplayName("Un JWT sin claim role no recibe autoridades")
    void jwtSinRolNoTieneAutoridades() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("usuario")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        assertThat(new JwtRoleAuthenticationConverter().convert(jwt).getAuthorities()).isEmpty();
    }
}
