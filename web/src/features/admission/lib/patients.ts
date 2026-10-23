import { differenceInYears, parse } from 'date-fns';

import type { Appointment, Patient, PatientLookup } from '@/lib/api';
import { formatShortDate, formatTime, todayIso } from '@/lib/formatters';

import { lookupToParams } from './documents';

/** Claves de TanStack Query del módulo de ventanilla (todas empiezan por 'admission'). */
export const admissionKeys = {
  all: ['admission'] as const,
  patientLookup: (lookup: PatientLookup) => ['admission', 'patient-lookup', lookup.documentType, lookup.documentNumber] as const,
  patient: (patientId: number) => ['admission', 'patient', patientId] as const,
  insurance: (patientId: number) => ['admission', 'insurance', patientId] as const,
  appointments: (patientId: number, upcoming: boolean) => ['admission', 'appointments', patientId, upcoming] as const,
  insurers: ['admission', 'insurers'] as const,
  locations: ['admission', 'locations'] as const,
  specialties: (locationId: number | null) => ['admission', 'specialties', locationId] as const,
  availability: (specialtyId: number, locationId: number, from: string, to: string) =>
    ['admission', 'availability', specialtyId, locationId, from, to] as const,
};

export function patientFullName(patient: Pick<Patient, 'firstNames' | 'lastNames'>): string {
  return `${patient.firstNames} ${patient.lastNames}`.trim();
}

/** Edad en años a partir de "yyyy-MM-dd"; null si la fecha no es válida. */
export function patientAge(birthDate: string): number | null {
  const date = parse(birthDate, 'yyyy-MM-dd', new Date());
  if (Number.isNaN(date.getTime())) return null;
  return differenceInYears(new Date(), date);
}

/** "Rímac Seguros · Red Médica" o "Particular". */
export function insuranceLabel(patient: Pick<Patient, 'insurance'>): string {
  return patient.insurance ? `${patient.insurance.insurer} · ${patient.insurance.plan}` : 'Particular';
}

function withLookup(path: string, patient: Patient, extra: Record<string, string> = {}): string {
  const params = new URLSearchParams({ ...lookupToParams(patient), ...extra });
  return `${path}?${params.toString()}`;
}

/** Enlace a la ficha del paciente; lleva el documento para que recargar la página vuelva a cargar sus datos. */
export function patientDetailPath(patient: Patient, tab?: 'datos' | 'seguro' | 'citas'): string {
  return withLookup(`/ventanilla/pacientes/${patient.id}`, patient, tab ? { tab } : {});
}

/** Enlace al asistente de agendar con el paciente precargado. */
export function bookingPath(patient: Patient): string {
  return withLookup('/ventanilla/agendar', patient, { paciente: String(patient.id) });
}

/** Enlace a la vista de citas con el paciente ya buscado. */
export function appointmentsPath(patient: Patient): string {
  return withLookup('/ventanilla/citas', patient);
}

/** Enlace al registro con el documento ya escrito (desde "no encontramos al paciente"). */
export function registerPatientPath(lookup: PatientLookup): string {
  return `/ventanilla/pacientes/nuevo?${new URLSearchParams(lookupToParams(lookup)).toString()}`;
}

/** "22 oct 2026 · 09:00 – 09:20" */
export function formatAppointmentWhen(appointment: Pick<Appointment, 'date' | 'startTime' | 'endTime'>): string {
  return `${formatShortDate(appointment.date)} · ${formatTime(appointment.startTime)} – ${formatTime(appointment.endTime)}`;
}

/** Solo las citas reservadas y todavía futuras admiten reprogramar o anular (CU-019, CU-020). */
export function isAppointmentActionable(appointment: Appointment, now = new Date()): boolean {
  if (appointment.status !== 'BOOKED') return false;
  const today = todayIso();
  if (appointment.date > today) return true;
  if (appointment.date < today) return false;
  const current = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
  return appointment.startTime > current;
}
