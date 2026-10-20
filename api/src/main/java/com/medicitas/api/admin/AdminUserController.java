package com.medicitas.api.admin;

import com.medicitas.api.admin.AdminUserDtos.CreateUserRequest;
import com.medicitas.api.admin.AdminUserDtos.PasswordChangeRequest;
import com.medicitas.api.admin.AdminUserDtos.UpdateUserRequest;
import com.medicitas.api.security.ClinicSecurityScheme;
import com.medicitas.api.security.UserRole;
import com.medicitas.api.security.UserSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Gestión de usuarios del panel de administración (solo {@link UserRole#ADMIN}, CU-020).
 * La ruta {@code /admin/**} exige el rol en {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Administración - Usuarios", description = "Usuarios del personal y sus perfiles")
@SecurityRequirement(name = ClinicSecurityScheme.BEARER)
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /**
     * Lista los usuarios del personal ordenados por nombre.
     *
     * @return los usuarios con su perfil, estado y último acceso
     */
    @GetMapping
    @Operation(summary = "Listar los usuarios del personal con su perfil y último acceso")
    public List<UserSummary> list() {
        return adminUserService.list();
    }

    /**
     * Consulta un usuario.
     *
     * @param id identificador del usuario
     * @return el usuario
     */
    @GetMapping("/{id}")
    @Operation(summary = "Consultar un usuario del personal")
    public UserSummary get(@PathVariable Long id) {
        return adminUserService.get(id);
    }

    /**
     * Crea un usuario del personal.
     *
     * @param request nombre de usuario, contraseña, perfil y médico vinculado (solo para médicos)
     * @return {@code 201 Created} con el usuario y la cabecera {@code Location}
     */
    @PostMapping
    @Operation(summary = "Crear un usuario del personal (RF-15)")
    public ResponseEntity<UserSummary> create(@Valid @RequestBody CreateUserRequest request) {
        UserSummary user = adminUserService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/users/" + user.id())).body(user);
    }

    /**
     * Cambia el perfil, el médico vinculado y el estado (activo o inactivo) de un usuario.
     *
     * @param id      identificador del usuario
     * @param request nuevos valores
     * @return el usuario actualizado
     */
    @PutMapping("/{id}")
    @Operation(summary = "Editar el perfil, el médico vinculado y el estado de un usuario (RF-15)")
    public UserSummary update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return adminUserService.update(id, request);
    }

    /**
     * Restablece la contraseña de un usuario.
     *
     * @param id      identificador del usuario
     * @param request nueva contraseña
     * @return {@code 204 No Content}
     */
    @PutMapping("/{id}/password")
    @Operation(summary = "Restablecer la contraseña de un usuario")
    public ResponseEntity<Void> changePassword(@PathVariable Long id, @Valid @RequestBody PasswordChangeRequest request) {
        adminUserService.changePassword(id, request);
        return ResponseEntity.noContent().build();
    }
}
