package com.medicitas.api.security;

import com.medicitas.api.common.BusinessException;
import com.medicitas.api.common.ErrorCodes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CurrentUserService")
class CurrentUserServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    private CurrentUserService currentUserService;
    private AppUser admin;

    @BeforeEach
    void preparar() {
        currentUserService = new CurrentUserService(appUserRepository);
        admin = new AppUser("admin", "$2a$10$hash", UserRole.ADMIN, null);
        ReflectionTestUtils.setField(admin, "id", 1L);
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Sin autenticación (kiosko) no hay usuario y no se consulta la base")
    void sinAutenticacionDevuelveVacio() {
        assertThat(currentUserService.current()).isEmpty();
        assertThat(currentUserService.currentUserId()).isEmpty();
        verifyNoInteractions(appUserRepository);
    }

    @Test
    @DisplayName("Con un JWT, el claim uid identifica al usuario en la base")
    void conJwtResuelveElUsuarioPorElClaimUid() {
        authenticateWithJwt(1L);
        when(appUserRepository.findWithDoctorById(1L)).thenReturn(Optional.of(admin));

        assertThat(currentUserService.currentUserId()).contains(1L);
        assertThat(currentUserService.current()).contains(admin);
        assertThat(currentUserService.require()).isSameAs(admin);
    }

    @Test
    @DisplayName("Una autenticación que no es JWT no se traduce a usuario")
    void autenticacionNoJwtDevuelveVacio() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        assertThat(currentUserService.current()).isEmpty();
        verifyNoInteractions(appUserRepository);
    }

    @Test
    @DisplayName("require() sin sesión lanza 401 UNAUTHORIZED")
    void requireSinSesionLanza401() {
        assertThatThrownBy(() -> currentUserService.require())
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCodes.UNAUTHORIZED);
                    assertThat(ex.getStatus().value()).isEqualTo(401);
                });
    }

    @Test
    @DisplayName("require() con un usuario desactivado lanza 403 USER_INACTIVE")
    void requireConUsuarioInactivoLanza403() {
        admin.setActive(false);
        authenticateWithJwt(1L);
        when(appUserRepository.findWithDoctorById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> currentUserService.require())
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCodes.USER_INACTIVE));
    }

    private static void authenticateWithJwt(long userId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("admin")
                .claim(JwtRoleAuthenticationConverter.ROLE_CLAIM, "ADMIN")
                .claim(JwtRoleAuthenticationConverter.USER_ID_CLAIM, userId)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtRoleAuthenticationConverter().convert(jwt));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isInstanceOf(JwtAuthenticationToken.class);
    }
}
