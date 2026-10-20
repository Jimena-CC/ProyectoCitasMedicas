package com.medicitas.api.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Convierte el JWT en la autenticación de Spring Security. En cada petición carga el usuario del claim
 * {@value #USER_ID_CLAIM}, comprueba que siga existiendo y activo, y construye la autoridad
 * {@code ROLE_<rol>} con el rol que el usuario tiene <em>ahora</em> en {@code app_user}: así, desactivar
 * a un usuario o cambiarle el rol surte efecto de inmediato aunque su token no haya vencido.
 * El claim {@value #ROLE_CLAIM} del token es solo informativo para el cliente.
 */
@Component
public class JwtRoleAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    /** Claim con el perfil del usuario ({@link UserRole}) en el momento de emitir el token; informativo. */
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
     * @return la autenticación con la autoridad del rol vigente del usuario en la base
     * @throws InvalidBearerTokenException si el token no referencia a un usuario existente
     * @throws DisabledException           si el usuario fue desactivado después de emitir el token
     */
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AppUser user = resolveUser(jwt);
        if (!user.isActive()) {
            throw new DisabledException("Usuario " + user.getUsername() + " desactivado");
        }
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(user.getRole().authority())),
                jwt.getSubject());
    }

    private AppUser resolveUser(Jwt jwt) {
        if (!(jwt.getClaim(USER_ID_CLAIM) instanceof Number uid)) {
            throw new InvalidBearerTokenException("El token no declara el claim " + USER_ID_CLAIM);
        }
        return appUserRepository.findById(uid.longValue())
                .orElseThrow(() -> new InvalidBearerTokenException("El token referencia a un usuario inexistente"));
    }
}
