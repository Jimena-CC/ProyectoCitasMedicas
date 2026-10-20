package com.medicitas.api.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Convierte el JWT en la autenticación de Spring Security: el claim {@value #ROLE_CLAIM}
 * se traduce a la autoridad {@code ROLE_<rol>} que usan las reglas de acceso.
 */
@Component
public class JwtRoleAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    /** Claim con el perfil del usuario ({@link UserRole}). */
    public static final String ROLE_CLAIM = "role";

    /** Claim con el id del usuario en {@code app_user}. */
    public static final String USER_ID_CLAIM = "uid";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String role = jwt.getClaimAsString(ROLE_CLAIM);
        List<GrantedAuthority> authorities = role == null
                ? List.of()
                : List.of(new SimpleGrantedAuthority("ROLE_" + role));
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
