package com.medicitas.api.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/appointments")
@Tag(name = "Notificaciones", description = "Trazabilidad de confirmaciones y recordatorios")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/{id}/notifications")
    @Operation(summary = "Consultar el estado de las notificaciones de una cita (RF-13)")
    public List<NotificationResponse> listByAppointment(@PathVariable Long id) {
        return notificationService.listByAppointment(id);
    }
}
