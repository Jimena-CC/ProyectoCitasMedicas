package com.medicitas.api.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Convierte el JWT en la autenticación de Spring Security: el claim {@value #ROLE_CLAIM}
 * se traduce a la autoridad {@code ROLE_<rol>} que usan las reglas de acceso.
 * En cada petición comprueba además que el usuario del claim {@value #USER_ID_CLAIM} siga existiendo
 * y activo, de modo que desactivar un usuario surte efecto de inmediato aunque su token no haya vencido.
 */
@Component
public class JwtRoleAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    /** Claim con el perfil del usuario ({@link UserRole}). */
    public static final String ROLE_CLAIM = "role";

    /** Claim con el id del usuario en {@code app_user}. */
    public static final String USER_ID_CLAIM = "uid";

    private final AppUserRepository appUserRepository;

    public JwtRoleAuthenticationConverter(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    /**
     * Construye la autenticación a partir de un token ya verificado (firma, vigencia y emisor).
     *
     * @param jwt token decodificado
     * @return la autenticación con la autoridad del rol declarado en el token
     * @throws InvalidBearerTokenException si el token no referencia a un usuario existente
     * @throws DisabledException           si el usuario fue desactivado después de emitir el token
     */
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AppUser user = resolveUser(jwt);
        if (!user.isActive()) {
            throw new DisabledException("Usuario " + user.getUsername() + " desactivado");
        }
        return new JwtAuthenticationToken(jwt, authoritiesOf(jwt), jwt.getSubject());
    }

    private AppUser resolveUser(Jwt jwt) {
        if (!(jwt.getClaim(USER_ID_CLAIM) instanceof Number uid)) {
            throw new InvalidBearerTokenException("El token no declara el claim " + USER_ID_CLAIM);
        }
        return appUserRepository.findById(uid.longValue())
                .orElseThrow(() -> new InvalidBearerTokenException("El token referencia a un usuario inexistente"));
    }

    private static List<GrantedAuthority> authoritiesOf(Jwt jwt) {
        String role = jwt.getClaimAsString(ROLE_CLAIM);
        if (role == null) {
            return List.of();
        }
        try {
            return List.of(new SimpleGrantedAuthority(UserRole.valueOf(role).authority()));
        } catch (IllegalArgumentException unknownRole) {
            return List.of();
        }
    }
}
