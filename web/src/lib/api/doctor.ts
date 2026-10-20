import { http } from '@/lib/api/client';
import type { AgendaItem, AgendaResponse, AttendanceRequest, IsoDate } from '@/lib/api/types';

/**
 * Módulo del médico (sección 7). Solo rol DOCTOR; el médico se resuelve del token.
 * Errores propios: 403 DOCTOR_NOT_LINKED, 404 APPOINTMENT_NOT_FOUND, 422 APPOINTMENT_NOT_MODIFIABLE.
 */

/** GET /doctor/agenda?date — por defecto hoy (America/Lima). */
export function getAgenda(date?: IsoDate | null): Promise<AgendaResponse> {
  return http.get<AgendaResponse>('/doctor/agenda', { date });
}

/** PATCH /doctor/appointments/{id}/attendance — devuelve el ítem de agenda actualizado. */
export function registerAttendance(appointmentId: number, body: AttendanceRequest): Promise<AgendaItem> {
  return http.patch<AgendaItem>(`/doctor/appointments/${appointmentId}/attendance`, body);
}

export const doctorApi = { getAgenda, registerAttendance };
