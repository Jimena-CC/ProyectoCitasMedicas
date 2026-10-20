package com.medicitas.api.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;

/**
 * Gestión de usuarios del panel de administración (solo {@link UserRole#ADMIN}).
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Administración - Usuarios", description = "Usuarios del personal y sus perfiles")
@SecurityRequirement(name = ClinicSecurityScheme.BEARER)
public class AdminUserController {

    private final AppUserRepository appUserRepository;
    private final Clock clock;

    public AdminUserController(AppUserRepository appUserRepository, Clock clock) {
        this.appUserRepository = appUserRepository;
        this.clock = clock;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Listar los usuarios del personal con su perfil y último acceso")
    public List<UserSummary> list() {
        return appUserRepository.findAllByOrderByUsernameAsc().stream()
                .map(user -> UserSummary.from(user, clock.getZone()))
                .toList();
    }
}
