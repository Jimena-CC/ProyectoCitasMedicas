package com.medicitas.api.security;

import com.medicitas.api.audit.AuditAction;
import com.medicitas.api.audit.AuditService;
import com.medicitas.api.common.BusinessException;
import com.medicitas.api.common.ErrorCodes;
import com.medicitas.api.security.AuthDtos.LoginRequest;
import com.medicitas.api.security.AuthDtos.LoginResponse;
import com.medicitas.api.security.AuthDtos.UserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Inicio de sesión del personal: valida las credenciales, exige que el usuario esté activo,
 * registra el último acceso y emite el JWT con los claims {@code sub}, {@code role} y {@code uid}.
 */
@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    /** Emisor declarado en el token. */
    public static final String ISSUER = "medicitas-api";

    /** Hash BCrypt de referencia para que un usuario inexistente tarde lo mismo que uno real. */
    private static final String DUMMY_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5XIu1gzuSxSQPvZXFhQ/6BmPpmOhO";

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;
    private final AuditService auditService;
    private final Clock clock;

    public AuthService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
                       SecurityProperties properties, AuditService auditService, Clock clock) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Valida las credenciales y emite un token.
     *
     * @param request usuario y contraseña
     * @return el token, su vencimiento y los datos del usuario
     * @throws BusinessException {@code 401 INVALID_CREDENTIALS} si el usuario o la contraseña no coinciden,
     *                           {@code 403 USER_INACTIVE} si el usuario está desactivado
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        Optional<AppUser> candidate = appUserRepository.findByUsernameIgnoreCase(request.username().trim());
        String hash = candidate.map(AppUser::getPasswordHash).orElse(DUMMY_HASH);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (candidate.isEmpty() || !matches) {
            logger.warn("Inicio de sesión rechazado para el usuario '{}'", request.username());
            throw invalidCredentials();
        }
        AppUser user = candidate.get();
        if (!user.isActive()) {
            throw userInactive();
        }

        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(Duration.ofMinutes(properties.jwt().expirationMinutes()));
        user.touchLastAccess(now);
        String token = issueToken(user, now, expiresAt);
        auditService.record(user, AuditAction.USER_LOGGED_IN, user.getId(),
                "Inicio de sesión de " + user.getUsername() + " (" + user.getRole() + ")");

        logger.info("Usuario {} ({}) inició sesión", user.getUsername(), user.getRole());
        return new LoginResponse(token, expiresAt.atZone(clock.getZone()).toOffsetDateTime(), UserInfo.from(user));
    }

    private String issueToken(AppUser user, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getUsername())
                .claim(JwtRoleAuthenticationConverter.ROLE_CLAIM, user.getRole().name())
                .claim(JwtRoleAuthenticationConverter.USER_ID_CLAIM, user.getId())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * Error de credenciales: el mensaje no revela si falló el usuario o la contraseña.
     *
     * @return la excepción con estado {@code 401}
     */
    public static BusinessException invalidCredentials() {
        return BusinessException.unauthorized(ErrorCodes.INVALID_CREDENTIALS, "Usuario o contraseña incorrectos.");
    }

    /**
     * Error para un usuario desactivado por el administrador.
     *
     * @return la excepción con estado {@code 403}
     */
    public static BusinessException userInactive() {
        return BusinessException.forbidden(ErrorCodes.USER_INACTIVE,
                "Tu usuario está desactivado. Comunícate con el administrador del sistema.");
    }
}
