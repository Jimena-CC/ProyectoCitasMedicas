package com.medicitas.api.doctor;

import com.medicitas.api.appointment.Appointment;
import com.medicitas.api.appointment.AppointmentStatus;
import com.medicitas.api.appointment.BookingChannel;
import com.medicitas.api.catalog.Doctor;
import com.medicitas.api.common.Formats;
import com.medicitas.api.patient.DocumentType;
import com.medicitas.api.patient.Patient;
import com.medicitas.api.schedule.Slot;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Cuerpos de petición y respuesta del módulo de médico (agenda del día y registro de asistencia).
 */
public final class DoctorDtos {

    private static final String SELF_PAY = "Particular";

    private DoctorDtos() {
    }

    /**
     * Resultado de la atención que el médico registra (RF-21, CU-012). Solo admite los dos estados finales:
     * cualquier otro valor se rechaza como {@code 400 INVALID_DATA} antes de llegar al servicio.
     */
    public enum AttendanceStatus {

        /** El paciente fue atendido. */
        ATTENDED(AppointmentStatus.ATTENDED),

        /** El paciente no se presentó. */
        NO_SHOW(AppointmentStatus.NO_SHOW);

        private final AppointmentStatus appointmentStatus;

        AttendanceStatus(AppointmentStatus appointmentStatus) {
            this.appointmentStatus = appointmentStatus;
        }

        /**
         * Estado de cita equivalente.
         *
         * @return el estado que toma la cita al registrar este resultado
         */
        public AppointmentStatus toAppointmentStatus() {
            return appointmentStatus;
        }
    }

    /**
     * Petición para registrar la asistencia de una cita.
     *
     * @param status resultado de la atención
     * @param note   observación opcional del médico, de hasta 250 caracteres
     */
    public record AttendanceRequest(
            @NotNull(message = "Indica si el paciente fue atendido o no se presentó.")
            AttendanceStatus status,

            @Size(max = 250, message = "La nota no puede superar los 250 caracteres.")
            String note) {
    }

    /**
     * Médico dueño de la agenda.
     *
     * @param id        id del médico
     * @param fullName  nombre completo
     * @param specialty nombre de la especialidad
     */
    public record DoctorSummary(Long id, String fullName, String specialty) {

        static DoctorSummary from(Doctor doctor) {
            return new DoctorSummary(doctor.getId(), doctor.getFullName(), doctor.getSpecialty().getName());
        }
    }

    /**
     * Paciente tal como lo ve el médico en su agenda.
     *
     * @param id             id del paciente
     * @param fullName       nombres y apellidos
     * @param documentType   tipo de documento
     * @param documentNumber número de documento
     * @param phone          celular de contacto
     */
    public record AgendaPatient(Long id, String fullName, DocumentType documentType, String documentNumber,
                                String phone) {

        static AgendaPatient from(Patient patient) {
            return new AgendaPatient(patient.getId(), patient.getFullName(), patient.getDocumentType(),
                    patient.getDocumentNumber(), patient.getPhone());
        }
    }

    /**
     * Conteo de citas del día por estado.
     *
     * @param total     todas las citas del día
     * @param booked    reservadas pendientes de atención
     * @param attended  atendidas
     * @param noShow    con inasistencia registrada
     * @param cancelled anuladas
     */
    public record AgendaSummary(int total, int booked, int attended, int noShow, int cancelled) {

        static AgendaSummary of(List<Appointment> appointments) {
            int booked = 0;
            int attended = 0;
            int noShow = 0;
            int cancelled = 0;
            for (Appointment appointment : appointments) {
                switch (appointment.getStatus()) {
                    case BOOKED -> booked++;
                    case ATTENDED -> attended++;
                    case NO_SHOW -> noShow++;
                    case CANCELLED -> cancelled++;
                }
            }
            return new AgendaSummary(appointments.size(), booked, attended, noShow, cancelled);
        }
    }

    /**
     * Una cita en la agenda del médico.
     *
     * @param appointmentId          id de la cita
     * @param code                   código visible de la cita
     * @param startTime              hora de inicio
     * @param endTime                hora de fin
     * @param room                   consultorio
     * @param location               nombre de la sede
     * @param patient                datos del paciente
     * @param visitReason            motivo de consulta
     * @param insurance              cobertura usada o {@code Particular}
     * @param status                 estado de la cita
     * @param bookingChannel         canal por el que se reservó
     * @param attendanceRegisteredAt instante en que se registró la asistencia; nulo si aún no se registró
     */
    public record AgendaItemResponse(
            Long appointmentId,
            String code,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Formats.TIME) LocalTime startTime,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Formats.TIME) LocalTime endTime,
            String room,
            String location,
            AgendaPatient patient,
            String visitReason,
            String insurance,
            AppointmentStatus status,
            BookingChannel bookingChannel,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Formats.DATE_TIME) OffsetDateTime attendanceRegisteredAt) {

        /**
         * Construye el item a partir de una cita con cupo, paciente y seguro ya cargados.
         * {@code attendanceRegisteredAt} es la última actualización de la cita cuando esta ya tiene asistencia
         * registrada, pues ese es el único cambio posible después de la reserva en ese estado.
         *
         * @param appointment cita con sus relaciones cargadas
         * @param zone        zona horaria de la clínica
         * @return el item de agenda
         */
        static AgendaItemResponse from(Appointment appointment, ZoneId zone) {
            Slot slot = appointment.getSlot();
            String insurance = appointment.getPatientInsurance() == null
                    ? SELF_PAY
                    : appointment.getPatientInsurance().getCoverageDescription();
            OffsetDateTime attendanceRegisteredAt = appointment.hasAttendanceRegistered()
                    ? appointment.getUpdatedAt().atZone(zone).toOffsetDateTime()
                    : null;
            return new AgendaItemResponse(appointment.getId(), appointment.getCode(), slot.getStartTime(),
                    slot.getEndTime(), slot.getRoom(), slot.getLocation().getName(),
                    AgendaPatient.from(appointment.getPatient()), appointment.getVisitReason(), insurance,
                    appointment.getStatus(), appointment.getBookingChannel(), attendanceRegisteredAt);
        }
    }

    /**
     * Agenda de un médico en una fecha.
     *
     * @param date    fecha consultada
     * @param doctor  médico dueño de la agenda
     * @param summary conteo por estado
     * @param items   citas ordenadas por hora de inicio
     */
    public record AgendaResponse(LocalDate date, DoctorSummary doctor, AgendaSummary summary,
                                 List<AgendaItemResponse> items) {
    }
}
