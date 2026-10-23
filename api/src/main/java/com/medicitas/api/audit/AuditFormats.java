package com.medicitas.api.audit;

import com.medicitas.api.patient.Patient;
import com.medicitas.api.schedule.Slot;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Formatos legibles para el detalle de la auditoría: el administrador lo lee tal cual, así que no lleva IDs
 * internos y las fechas van en {@code dd/MM/yyyy}.
 */
public final class AuditFormats {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private AuditFormats() {
    }

    /**
     * @param date fecha
     * @return la fecha como {@code 21/10/2026}
     */
    public static String date(LocalDate date) {
        return DATE.format(date);
    }

    /**
     * @param slot cupo con su médico cargado
     * @return fecha, hora de inicio y médico, p. ej. {@code 21/10/2026 09:00, Mateo Prueba Rivas}
     */
    public static String slot(Slot slot) {
        return date(slot.getDate()) + " " + TIME.format(slot.getStartTime()) + ", " + slot.getDoctor().getFullName();
    }

    /**
     * @param patient paciente
     * @return nombre y documento, p. ej. {@code Lucía Paredes Quispe (DNI 45871236)}
     */
    public static String patient(Patient patient) {
        return patient.getFullName() + " (" + patient.getDocumentType().name() + " " + patient.getDocumentNumber() + ")";
    }
}
