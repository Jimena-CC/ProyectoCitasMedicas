package com.medicitas.api.admin;

import com.medicitas.api.admin.AdminUserDtos.CreateUserRequest;
import com.medicitas.api.admin.AdminUserDtos.PasswordChangeRequest;
import com.medicitas.api.admin.AdminUserDtos.UpdateUserRequest;
import com.medicitas.api.audit.AuditAction;
import com.medicitas.api.audit.AuditService;
import com.medicitas.api.catalog.Doctor;
import com.medicitas.api.catalog.DoctorRepository;
import com.medicitas.api.common.BusinessException;
import com.medicitas.api.common.ErrorCodes;
import com.medicitas.api.security.AppUser;
import com.medicitas.api.security.AppUserRepository;
import com.medicitas.api.security.CurrentUserService;
import com.medicitas.api.security.UserRole;
import com.medicitas.api.security.UserSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Gestión de los usuarios del personal por el administrador (CU-020, RF-15/RF-16): alta, edición de perfil,
 * médico vinculado y estado, y restablecimiento de contraseña. Cada cambio queda en {@code audit_log}
 * atribuido al administrador que lo hizo; los detalles nunca incluyen contraseñas.
 */
@Service
public class AdminUserService {

    private static final Logger logger = LoggerFactory.getLogger(AdminUserService.class);

    private final AppUserRepository appUserRepository;
    private final DoctorRepository doctorRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public AdminUserService(AppUserRepository appUserRepository, DoctorRepository doctorRepository,
                            PasswordEncoder passwordEncoder, AuditService auditService,
                            CurrentUserService currentUserService, Clock clock) {
        this.appUserRepository = appUserRepository;
        this.doctorRepository = doctorRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    /**
     * Lista los usuarios del personal ordenados por nombre de usuario.
     *
     * @return los usuarios con su perfil, estado y último acceso
     */
    @Transactional(readOnly = true)
    public List<UserSummary> list() {
        return appUserRepository.findAllByOrderByUsernameAsc().stream()
                .map(this::toSummary)
                .toList();
    }

    /**
     * Busca un usuario por id.
     *
     * @param id identificador del usuario
     * @return el usuario
     * @throws BusinessException {@code 404 RESOURCE_NOT_FOUND} si no existe
     */
    @Transactional(readOnly = true)
    public UserSummary get(Long id) {
        return toSummary(find(id));
    }

    /**
     * Crea un usuario activo con la contraseña cifrada.
     *
     * @param request datos del usuario
     * @return el usuario creado
     * @throws BusinessException {@code 409 USERNAME_TAKEN} si el nombre ya existe (sin distinguir mayúsculas),
     *                           {@code 400 INVALID_DATA} si el médico vinculado no corresponde al perfil,
     *                           {@code 404 RESOURCE_NOT_FOUND} si el médico no existe,
     *                           {@code 409 DOCTOR_ALREADY_LINKED} si el médico ya tiene usuario
     */
    @Transactional
    public UserSummary create(CreateUserRequest request) {
        String username = request.username().trim();
        if (appUserRepository.existsByUsernameIgnoreCase(username)) {
            throw BusinessException.conflict(ErrorCodes.USERNAME_TAKEN,
                    "Ya existe un usuario con ese nombre. Elige otro nombre de usuario.");
        }
        Doctor doctor = resolveDoctor(request.role(), request.doctorId(), null);
        AppUser user = appUserRepository.save(
                new AppUser(username, passwordEncoder.encode(request.password()), request.role(), doctor));
        auditService.record(AuditAction.USER_CREATED, user.getId(),
                "Usuario " + user.getUsername() + " creado con perfil " + user.getRole().label() + describe(doctor));
        logger.info("Usuario {} creado con perfil {}", user.getUsername(), user.getRole());
        return toSummary(user);
    }

    /**
     * Cambia el perfil, el médico vinculado y el estado de un usuario. El cambio aplica en la siguiente
     * petición del usuario afectado, porque la autorización lee el rol y el estado guardados en la base.
     *
     * @param id      identificador del usuario
     * @param request nuevos valores
     * @return el usuario actualizado
     * @throws BusinessException {@code 404 RESOURCE_NOT_FOUND} si no existe,
     *                           {@code 422 SELF_DEACTIVATION} si el administrador intenta desactivarse o
     *                           quitarse el perfil de administrador, y los mismos errores de médico vinculado
     *                           que {@link #create(CreateUserRequest)}
     */
    @Transactional
    public UserSummary update(Long id, UpdateUserRequest request) {
        AppUser user = find(id);
        AppUser actor = currentUserService.require();
        if (actor.getId().equals(user.getId())) {
            if (!request.active()) {
                throw BusinessException.ruleViolated(ErrorCodes.SELF_DEACTIVATION,
                        "No puedes desactivar tu propio usuario. Pídeselo a otro administrador.");
            }
            if (request.role() != UserRole.ADMIN) {
                throw BusinessException.ruleViolated(ErrorCodes.SELF_DEACTIVATION,
                        "No puedes quitarte el perfil de administrador. Pídeselo a otro administrador.");
            }
        }
        Doctor doctor = resolveDoctor(request.role(), request.doctorId(), user.getId());
        boolean wasActive = user.isActive();
        user.changeRole(request.role());
        user.linkDoctor(doctor);
        user.setActive(request.active());

        if (wasActive && !request.active()) {
            auditService.record(AuditAction.USER_DEACTIVATED, user.getId(),
                    "Usuario " + user.getUsername() + " desactivado (perfil " + user.getRole().label() + ")");
        } else {
            auditService.record(AuditAction.USER_UPDATED, user.getId(),
                    "Usuario " + user.getUsername() + " actualizado: perfil " + user.getRole().label()
                            + (request.active() ? ", activo" : ", inactivo") + describe(doctor));
        }
        logger.info("Usuario {} actualizado: perfil {}, activo {}", user.getUsername(), user.getRole(), user.isActive());
        return toSummary(user);
    }

    /**
     * Restablece la contraseña de un usuario.
     *
     * @param id      identificador del usuario
     * @param request nueva contraseña
     * @throws BusinessException {@code 404 RESOURCE_NOT_FOUND} si no existe
     */
    @Transactional
    public void changePassword(Long id, PasswordChangeRequest request) {
        AppUser user = find(id);
        user.changePassword(passwordEncoder.encode(request.password()));
        auditService.record(AuditAction.USER_PASSWORD_CHANGED, user.getId(),
                "Contraseña restablecida para el usuario " + user.getUsername());
        logger.info("Contraseña restablecida para el usuario {}", user.getUsername());
    }

    /**
     * Médico que corresponde al perfil: obligatorio y sin otro usuario para {@code DOCTOR}, ausente para el resto.
     *
     * @param role          perfil solicitado
     * @param doctorId      médico indicado en la petición, puede ser nulo
     * @param currentUserId usuario que se edita (se excluye de la comprobación de médico ya vinculado), o
     *                      {@code null} en el alta
     * @return el médico a vincular o {@code null}
     */
    private Doctor resolveDoctor(UserRole role, Long doctorId, Long currentUserId) {
        if (role != UserRole.DOCTOR) {
            if (doctorId != null) {
                throw BusinessException.invalidData("doctorId", "Solo el perfil Médico lleva un médico vinculado.");
            }
            return null;
        }
        if (doctorId == null) {
            throw BusinessException.invalidData("doctorId", "Selecciona el médico vinculado al usuario.");
        }
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCodes.RESOURCE_NOT_FOUND,
                        "El médico seleccionado no existe."));
        Optional<AppUser> linked = appUserRepository.findByDoctorId(doctorId);
        if (linked.isPresent() && !linked.get().getId().equals(currentUserId)) {
            throw BusinessException.conflict(ErrorCodes.DOCTOR_ALREADY_LINKED,
                    "Ese médico ya tiene un usuario asignado (" + linked.get().getUsername() + ").");
        }
        return doctor;
    }

    private AppUser find(Long id) {
        return appUserRepository.findWithDoctorById(id)
                .orElseThrow(() -> BusinessException.notFound(ErrorCodes.RESOURCE_NOT_FOUND, "El usuario no existe."));
    }

    private UserSummary toSummary(AppUser user) {
        return UserSummary.from(user, clock.getZone());
    }

    private static String describe(Doctor doctor) {
        return doctor == null ? "" : ", médico vinculado " + doctor.getFullName();
    }
}
