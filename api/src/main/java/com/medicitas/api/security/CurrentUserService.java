package com.medicitas.api.security;

import com.medicitas.api.common.BusinessException;
import com.medicitas.api.common.ErrorCodes;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Resuelve el {@link AppUser} de la petición en curso a partir del JWT.
 * Las llamadas públicas del kiosko no llevan token: en ese caso no hay usuario.
 */
@Service
public class CurrentUserService {

    private final AppUserRepository appUserRepository;

    public CurrentUserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    /**
     * Usuario autenticado en la petición actual.
     *
     * @return el usuario, o vacío si la petición es anónima (kiosko) o el token no referencia a un usuario existente
     */
    @Transactional(readOnly = true)
    public Optional<AppUser> current() {
        return currentUserId().flatMap(appUserRepository::findWithDoctorById);
    }

    /**
     * Usuario autenticado y activo; para endpoints que exigen sesión.
     *
     * @return el usuario autenticado
     * @throws BusinessException {@code 401 UNAUTHORIZED} si no hay sesión, {@code 403 USER_INACTIVE} si fue desactivado
     */
    @Transactional(readOnly = true)
    public AppUser require() {
        AppUser user = current().orElseThrow(() -> BusinessException.unauthorized(ErrorCodes.UNAUTHORIZED,
                "Debes iniciar sesión para continuar."));
        if (!user.isActive()) {
            throw AuthService.userInactive();
        }
        return user;
    }

    /**
     * Id de usuario declarado en el claim {@code uid} del token, sin consultar la base.
     *
     * @return el id, o vacío si la petición es anónima
     */
    public Optional<Long> currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token
                && token.getToken().getClaim(JwtRoleAuthenticationConverter.USER_ID_CLAIM) instanceof Number uid) {
            return Optional.of(uid.longValue());
        }
        return Optional.empty();
    }
}
