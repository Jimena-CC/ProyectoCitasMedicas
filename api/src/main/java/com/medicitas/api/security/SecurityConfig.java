package com.medicitas.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Reglas de acceso de la API: los endpoints del kiosko son públicos y los paneles del personal
 * exigen un token JWT con el rol correspondiente. La API no guarda sesión ni usa CSRF.
 * No se declara {@code UserDetailsService}: el login lo resuelve {@link AuthService} y, al existir un
 * {@link JwtDecoder}, Spring Boot no genera el usuario "user" con contraseña aleatoria.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Longitud mínima de la clave HMAC para HS256 (256 bits). */
    private static final int MIN_SECRET_BYTES = 32;

    /**
     * Rutas del kiosko y de la documentación que no requieren token.
     * Los prefijos del kiosko ({@code /patients}, {@code /appointments}, etc.) se abren completos porque
     * el paciente nunca inicia sesión. Por eso, todo endpoint nuevo del personal debe publicarse bajo
     * {@code /api/v1/doctor/**}, {@code /api/v1/front-desk/**} o {@code /api/v1/admin/**}; si se colocara
     * bajo un prefijo del kiosko quedaría público sin querer.
     */
    static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/login",
            "/api/v1/patients/**",
            "/api/v1/appointments/**",
            "/api/v1/availability/**",
            "/api/v1/specialties/**",
            "/api/v1/locations/**",
            "/api/v1/insurers/**",
            "/api/v1/notifications/**",
            "/actuator/health",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    /**
     * Cadena de filtros de la API: rutas públicas del kiosko, rutas del personal por rol y validación
     * del token Bearer con respuestas de error en el formato del contrato.
     *
     * @param http                    configuración de seguridad web
     * @param jwtDecoder              decodificador que verifica firma, vigencia y emisor del token
     * @param authenticationConverter traduce el token a la autenticación con su rol
     * @param entryPoint              responde {@code 401} o {@code 403 USER_INACTIVE} según la causa
     * @param accessDeniedHandler     responde {@code 403} cuando el rol no corresponde
     * @param corsConfigurationSource orígenes del navegador autorizados
     * @return la cadena de filtros
     * @throws Exception si la configuración no puede construirse
     */
    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                                      JwtRoleAuthenticationConverter authenticationConverter,
                                                      ApiAuthenticationEntryPoint entryPoint,
                                                      ApiAccessDeniedHandler accessDeniedHandler,
                                                      CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers("/api/v1/doctor/**").hasRole(UserRole.DOCTOR.name())
                        .requestMatchers("/api/v1/front-desk/**")
                        .hasAnyRole(UserRole.FRONT_DESK.name(), UserRole.ADMIN.name())
                        .requestMatchers("/api/v1/admin/**").hasRole(UserRole.ADMIN.name())
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(authenticationConverter))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    /**
     * Configuración CORS para los paneles web del personal: orígenes de {@code security.cors.allowed-origins},
     * métodos REST habituales y las cabeceras {@code Authorization}, {@code Content-Type} e {@code Idempotency-Key}.
     *
     * @param properties propiedades de seguridad
     * @return la fuente de configuración CORS aplicada a toda la API
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.cors().allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        configuration.setExposedHeaders(List.of("Location"));
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Codificador BCrypt con el que se guardan y verifican las contraseñas de {@code app_user}.
     *
     * @return el codificador de contraseñas
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Firma los tokens con HS256 y la clave simétrica de {@code security.jwt.secret}.
     *
     * @param properties propiedades de seguridad
     * @return el codificador de tokens
     */
    @Bean
    public JwtEncoder jwtEncoder(SecurityProperties properties) {
        return NimbusJwtEncoder.withSecretKey(secretKey(properties)).algorithm(MacAlgorithm.HS256).build();
    }

    /**
     * Verifica la firma, la vigencia y el emisor ({@link AuthService#ISSUER}) de los tokens recibidos.
     *
     * @param properties propiedades de seguridad
     * @return el decodificador de tokens
     */
    @Bean
    public JwtDecoder jwtDecoder(SecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(AuthService.ISSUER));
        return decoder;
    }

    private static SecretKey secretKey(SecurityProperties properties) {
        String secret = properties.jwt().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "security.jwt.secret debe tener al menos " + MIN_SECRET_BYTES + " caracteres");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
