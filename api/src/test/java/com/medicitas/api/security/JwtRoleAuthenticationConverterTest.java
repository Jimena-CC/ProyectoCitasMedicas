package com.medicitas.api.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtRoleAuthenticationConverter")
class JwtRoleAuthenticationConverterTest {

    @Mock
    private AppUserRepository appUserRepository;

    private JwtRoleAuthenticationConverter converter;
    private AppUser admin;

    @BeforeEach
    void preparar() {
        converter = new JwtRoleAuthenticationConverter(appUserRepository);
        admin = new AppUser("admin", "$2a$10$hash", UserRole.ADMIN, null);
        ReflectionTestUtils.setField(admin, "id", 1L);
    }

    @ParameterizedTest(name = "claim role={0} -> ROLE_{0}")
    @EnumSource(UserRole.class)
    @DisplayName("Con un usuario activo, el claim role se traduce a la autoridad ROLE_<perfil>")
    void usuarioActivoRecibeLaAutoridadDeSuRol(UserRole role) {
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));

        AbstractAuthenticationToken authentication = converter.convert(jwt(1L, role.name()));

        assertAll(
                () -> assertThat(authentication).isInstanceOf(JwtAuthenticationToken.class),
                () -> assertThat(authentication.getName()).isEqualTo("admin"),
                () -> assertThat(authentication.getAuthorities())
                        .containsExactly(new SimpleGrantedAuthority(role.authority())));
    }

    @Test
    @DisplayName("Un usuario desactivado tras emitir el token es rechazado con DisabledException")
    void usuarioInactivoEsRechazado() {
        admin.setActive(false);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> converter.convert(jwt(1L, "ADMIN")))
                .isInstanceOf(DisabledException.class);
    }

    @Test
    @DisplayName("Un token que referencia a un usuario inexistente no es válido")
    void usuarioInexistenteEsRechazado() {
        when(appUserRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> converter.convert(jwt(99L, "ADMIN")))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    @DisplayName("Un token sin claim uid no es válido y no consulta la base")
    void tokenSinUidEsRechazado() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("admin")
                .claim(JwtRoleAuthenticationConverter.ROLE_CLAIM, "ADMIN")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(InvalidBearerTokenException.class);
        verifyNoInteractions(appUserRepository);
    }

    @Test
    @DisplayName("Un claim role ausente o desconocido no otorga autoridades")
    void rolAusenteODesconocidoNoOtorgaAutoridades() {
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertAll(
                () -> assertThat(converter.convert(jwt(1L, null)).getAuthorities()).isEmpty(),
                () -> assertThat(converter.convert(jwt(1L, "SUPERUSER")).getAuthorities()).isEmpty());
    }

    private static Jwt jwt(long userId, String role) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("admin")
                .claim(JwtRoleAuthenticationConverter.USER_ID_CLAIM, userId)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (role != null) {
            builder.claim(JwtRoleAuthenticationConverter.ROLE_CLAIM, role);
        }
        return builder.build();
    }
}
