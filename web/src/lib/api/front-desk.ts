import { http, newIdempotencyKey } from '@/lib/api/client';
import type {
  Appointment,
  AppointmentRequest,
  CancellationRequest,
  InsuranceRequest,
  InsuranceValidationResponse,
  Patient,
  PatientLookup,
  PatientRequest,
  RescheduleRequest,
} from '@/lib/api/types';

/**
 * Módulo de admisión (sección 8). Roles FRONT_DESK y ADMIN.
 * La disponibilidad se consulta con catalogApi.getAvailability (endpoint público).
 */

/** GET /front-desk/patients?documentType&documentNumber — 404 PATIENT_NOT_FOUND. */
export function searchPatient(lookup: PatientLookup): Promise<Patient> {
  return http.get<Patient>('/front-desk/patients', { ...lookup });
}

/** POST /front-desk/patients — 201; 409 DUPLICATE_DOCUMENT; 400 INVALID_DATA con fields. */
export function createPatient(body: PatientRequest): Promise<Patient> {
  return http.post<Patient>('/front-desk/patients', body);
}

/** GET /front-desk/patients/{id}/insurance — validación de cobertura (CU-017). */
export function getInsuranceValidation(patientId: number): Promise<InsuranceValidationResponse> {
  return http.get<InsuranceValidationResponse>(`/front-desk/patients/${patientId}/insurance`);
}

/** PUT /front-desk/patients/{id}/insurance — planId null = particular. */
export function updateInsurance(patientId: number, body: InsuranceRequest): Promise<Patient> {
  return http.put<Patient>(`/front-desk/patients/${patientId}/insurance`, body);
}

/** GET /front-desk/patients/{id}/appointments?upcoming — upcoming=false devuelve el historial completo. */
export function listPatientAppointments(patientId: number, upcoming = true): Promise<Appointment[]> {
  return http.get<Appointment[]>(`/front-desk/patients/${patientId}/appointments`, { upcoming });
}

/**
 * POST /front-desk/appointments — exige Idempotency-Key (UUID v4).
 * Genera una clave por intento con newIdempotencyKey() y reutilízala si reintentas tras un corte de red.
 * Errores: 409 SLOT_NOT_AVAILABLE · 409 DUPLICATE_APPOINTMENT · 422 COVERAGE_NOT_ACTIVE · 404.
 */
export function bookAppointment(body: AppointmentRequest, idempotencyKey: string = newIdempotencyKey()): Promise<Appointment> {
  return http.post<Appointment>('/front-desk/appointments', body, { idempotencyKey });
}

/** PATCH /front-desk/appointments/{id}/reschedule — misma especialidad; 422 APPOINTMENT_NOT_MODIFIABLE. */
export function rescheduleAppointment(appointmentId: number, body: RescheduleRequest): Promise<Appointment> {
  return http.patch<Appointment>(`/front-desk/appointments/${appointmentId}/reschedule`, body);
}

/** PATCH /front-desk/appointments/{id}/cancellation — el motivo es obligatorio (≤ 200). */
export function cancelAppointment(appointmentId: number, body: CancellationRequest): Promise<Appointment> {
  return http.patch<Appointment>(`/front-desk/appointments/${appointmentId}/cancellation`, body);
}

export const frontDeskApi = {
  searchPatient,
  createPatient,
  getInsuranceValidation,
  updateInsurance,
  listPatientAppointments,
  bookAppointment,
  rescheduleAppointment,
  cancelAppointment,
};
