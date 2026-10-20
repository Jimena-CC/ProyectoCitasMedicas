package com.medicitas.api.security;

import com.medicitas.api.security.AuthDtos.LoginRequest;
import com.medicitas.api.security.AuthDtos.LoginResponse;
import com.medicitas.api.security.AuthDtos.UserInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Inicio de sesión del personal y consulta del usuario autenticado")
public class AuthController {

    private final AuthService authService;
    private final CurrentUserService currentUserService;

    public AuthController(AuthService authService, CurrentUserService currentUserService) {
        this.authService = authService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión con usuario y contraseña; devuelve el token Bearer")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @SecurityRequirement(name = ClinicSecurityScheme.BEARER)
    @Operation(summary = "Datos del usuario autenticado")
    public UserInfo me() {
        return UserInfo.from(currentUserService.require());
    }
}
