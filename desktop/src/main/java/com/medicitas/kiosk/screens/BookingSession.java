package com.medicitas.kiosk.screens;

import com.medicitas.kiosk.api.Dtos;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Estado del paciente en el kiosko durante una visita. Se descarta por completo al finalizar
 * o al vencer el tiempo de inactividad, para que el siguiente paciente no vea datos ajenos.
 */
public class BookingSession {

    public enum Step {
        IDENTIFICATION("Identificación"),
        PERSONAL_DATA_AND_INSURANCE("Datos y seguro"),
        SPECIALTY_AND_LOCATION("Especialidad y sede"),
        DATE_AND_TIME("Fecha y hora"),
        CONFIRMATION("Confirmación");

        private final String title;

        Step(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }

        public int number() {
            return ordinal() + 1;
        }
    }

    public enum Mode { BOOKING, RESCHEDULE }

    private Step currentStep = Step.IDENTIFICATION;
    private Step maxStep = Step.IDENTIFICATION;
    private Mode mode = Mode.BOOKING;

    private String documentType = "DNI";
    private String documentNumber;
    private Dtos.Patient patient;
    private boolean newPatient;
    private boolean insuranceSet;

    private Dtos.Location location;
    private Dtos.Specialty specialty;
    private LocalDate slotDate;
    private Dtos.Slot slot;
    private String visitReason;

    private UUID idempotencyKey;
    private Dtos.Appointment confirmedAppointment;
    private Dtos.Appointment appointmentToReschedule;

    public Step getCurrentStep() {
        return currentStep;
    }

    public Step getMaxStep() {
        return maxStep;
    }

    /** Indica si los datos del paso dado están completos para continuar. */
    public boolean isStepComplete(Step step) {
        return switch (step) {
            case IDENTIFICATION -> patient != null || (newPatient && documentNumber != null);
            case PERSONAL_DATA_AND_INSURANCE -> patient != null && insuranceSet;
            case SPECIALTY_AND_LOCATION -> location != null && specialty != null;
            case DATE_AND_TIME -> slot != null && slotDate != null;
            case CONFIRMATION -> Validations.visitReason(visitReason).isEmpty();
        };
    }

    public boolean advance() {
        if (currentStep == Step.CONFIRMATION || !isStepComplete(currentStep)) {
            return false;
        }
        currentStep = Step.values()[currentStep.ordinal() + 1];
        if (currentStep.ordinal() > maxStep.ordinal()) {
            maxStep = currentStep;
        }
        return true;
    }

    public boolean goBack() {
        Step limit = mode == Mode.RESCHEDULE ? Step.DATE_AND_TIME : Step.IDENTIFICATION;
        if (currentStep.ordinal() <= limit.ordinal()) {
            return false;
        }
        currentStep = Step.values()[currentStep.ordinal() - 1];
        return true;
    }

    /** Permite volver a un paso ya visitado desde la barra lateral. */
    public boolean goTo(Step step) {
        if (step.ordinal() > maxStep.ordinal()) {
            return false;
        }
        currentStep = step;
        return true;
    }

    public void documentEntered(String type, String number) {
        if (!type.equals(documentType) || !number.equals(documentNumber)) {
            patient = null;
            newPatient = false;
            insuranceSet = false;
        }
        documentType = type;
        documentNumber = number;
    }

    public void patientFound(Dtos.Patient found) {
        patient = found;
        newPatient = false;
        insuranceSet = true;
    }

    public void patientNotRegistered() {
        patient = null;
        newPatient = true;
        insuranceSet = false;
    }

    public void patientRegistered(Dtos.Patient registered) {
        patient = registered;
    }

    /** Se llama tras guardar el seguro o elegir atención particular. */
    public void insuranceUpdated(Dtos.Patient updated) {
        patient = updated;
        insuranceSet = true;
    }

    public void selectLocation(Dtos.Location newLocation) {
        if (location == null || !location.id().equals(newLocation.id())) {
            specialty = null;
            clearSlot();
        }
        location = newLocation;
    }

    public void selectSpecialty(Dtos.Specialty newSpecialty) {
        if (specialty == null || !specialty.id().equals(newSpecialty.id())) {
            clearSlot();
        }
        specialty = newSpecialty;
    }

    public void selectSlot(LocalDate date, Dtos.Slot newSlot) {
        if (slot == null || !slot.slotId().equals(newSlot.slotId())) {
            idempotencyKey = null;
        }
        slotDate = date;
        slot = newSlot;
    }

    public void setVisitReason(String visitReason) {
        this.visitReason = visitReason;
    }

    /** Clave del intento actual de reserva: se genera una vez y se reutiliza en cada reintento (RNF-07). */
    public UUID bookingKey() {
        if (idempotencyKey == null) {
            idempotencyKey = UUID.randomUUID();
        }
        return idempotencyKey;
    }

    /** El cupo ya no está disponible o genera cruce: se vuelve a elegir horario con una clave nueva. */
    public void scheduleConflict() {
        clearSlot();
        currentStep = Step.DATE_AND_TIME;
    }

    public void bookingConfirmed(Dtos.Appointment appointment) {
        confirmedAppointment = appointment;
        idempotencyKey = null;
    }

    /** Prepara la sesión para reprogramar una cita: se salta directo a elegir fecha y hora. */
    public void startReschedule(Dtos.Appointment appointment, Dtos.Location appointmentLocation,
                                Dtos.Specialty appointmentSpecialty) {
        mode = Mode.RESCHEDULE;
        appointmentToReschedule = appointment;
        confirmedAppointment = null;
        location = appointmentLocation;
        specialty = appointmentSpecialty;
        clearSlot();
        currentStep = Step.DATE_AND_TIME;
        maxStep = Step.DATE_AND_TIME;
    }

    /** Vuelve al flujo normal de reserva conservando al paciente identificado. */
    public void newBooking() {
        mode = Mode.BOOKING;
        appointmentToReschedule = null;
        confirmedAppointment = null;
        location = null;
        specialty = null;
        visitReason = null;
        clearSlot();
        currentStep = Step.SPECIALTY_AND_LOCATION;
        maxStep = Step.SPECIALTY_AND_LOCATION;
    }

    /** Borra todos los datos del paciente (fin de la visita o inactividad). */
    public void clear() {
        currentStep = Step.IDENTIFICATION;
        maxStep = Step.IDENTIFICATION;
        mode = Mode.BOOKING;
        documentType = "DNI";
        documentNumber = null;
        patient = null;
        newPatient = false;
        insuranceSet = false;
        location = null;
        specialty = null;
        visitReason = null;
        confirmedAppointment = null;
        appointmentToReschedule = null;
        clearSlot();
    }

    private void clearSlot() {
        slot = null;
        slotDate = null;
        idempotencyKey = null;
    }

    public Mode getMode() {
        return mode;
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public Dtos.Patient getPatient() {
        return patient;
    }

    public boolean isNewPatient() {
        return newPatient;
    }

    public Dtos.Location getLocation() {
        return location;
    }

    public Dtos.Specialty getSpecialty() {
        return specialty;
    }

    public LocalDate getSlotDate() {
        return slotDate;
    }

    public Dtos.Slot getSlot() {
        return slot;
    }

    public String getVisitReason() {
        return visitReason;
    }

    public Dtos.Appointment getConfirmedAppointment() {
        return confirmedAppointment;
    }

    public Dtos.Appointment getAppointmentToReschedule() {
        return appointmentToReschedule;
    }
}
