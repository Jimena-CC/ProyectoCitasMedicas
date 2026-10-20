package com.medicitas.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Responde con el formato {@link com.medicitas.api.common.ErrorResponse} cuando una ruta protegida se
 * llama sin token o con un token inválido ({@code 401}), o cuando el token pertenece a un usuario que fue
 * desactivado ({@code 403 USER_INACTIVE}). Delega en el resolutor de excepciones de MVC para que
 * {@link com.medicitas.api.common.GlobalExceptionHandler} construya la respuesta.
 */
@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final HandlerExceptionResolver exceptionResolver;

    public ApiAuthenticationEntryPoint(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) {
        Exception resolved = authException instanceof DisabledException
                ? AuthService.userInactive()
                : authException;
        exceptionResolver.resolveException(request, response, null, resolved);
    }
}
