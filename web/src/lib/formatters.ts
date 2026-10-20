import { format, isValid, parse, parseISO } from 'date-fns';
import { es } from 'date-fns/locale';

import type { AppointmentStatus, DocumentType, SlotStatus, UserRole } from '@/lib/api/types';

/**
 * Formateadores de presentación. Todas las salidas están en español (RNF-16).
 * Las fechas "yyyy-MM-dd" se interpretan como fecha local (sin desfase de zona horaria).
 */

function toDate(value: string | Date): Date | null {
  if (value instanceof Date) return isValid(value) ? value : null;
  const parsed = /^\d{4}-\d{2}-\d{2}$/.test(value) ? parse(value, 'yyyy-MM-dd', new Date()) : parseISO(value);
  return isValid(parsed) ? parsed : null;
}

/** "2026-10-20" → "martes, 20 de octubre de 2026" */
export function formatLongDate(value: string | Date | null | undefined): string {
  if (!value) return '—';
  const date = toDate(value);
  return date ? format(date, "EEEE, d 'de' MMMM 'de' yyyy", { locale: es }) : '—';
}

/** "2026-10-20" → "20 oct 2026" */
export function formatShortDate(value: string | Date | null | undefined): string {
  if (!value) return '—';
  const date = toDate(value);
  return date ? format(date, 'd MMM yyyy', { locale: es }) : '—';
}

/** "2026-10-20" → "20/10/2026" */
export function formatNumericDate(value: string | Date | null | undefined): string {
  if (!value) return '—';
  const date = toDate(value);
  return date ? format(date, 'dd/MM/yyyy') : '—';
}

/** "09:05" o un instante ISO → "09:05" */
export function formatTime(value: string | Date | null | undefined): string {
  if (!value) return '—';
  if (typeof value === 'string' && /^\d{2}:\d{2}(:\d{2})?$/.test(value)) return value.slice(0, 5);
  const date = toDate(value);
  return date ? format(date, 'HH:mm') : '—';
}

/** "2026-10-20T09:25:10-05:00" → "20 oct 2026, 09:25" */
export function formatDateTime(value: string | Date | null | undefined): string {
  if (!value) return '—';
  const date = toDate(value);
  return date ? format(date, "d MMM yyyy, HH:mm", { locale: es }) : '—';
}

/** Fecha de hoy como "yyyy-MM-dd" (zona local del navegador). */
export function todayIso(): string {
  return format(new Date(), 'yyyy-MM-dd');
}

/** Date → "yyyy-MM-dd" para enviar a la API. */
export function toIsoDate(date: Date): string {
  return format(date, 'yyyy-MM-dd');
}

export const DOCUMENT_TYPE_LABELS: Record<DocumentType, string> = {
  DNI: 'DNI',
  CE: 'Carné de extranjería',
  PAS: 'Pasaporte',
};

/** ("DNI", "45871236") → "DNI 45871236" */
export function formatDocument(type: DocumentType | string, number: string): string {
  return `${type} ${number}`;
}

/** 45 → "S/ 45.00" */
export function formatCurrency(amount: number | null | undefined): string {
  if (amount === null || amount === undefined) return '—';
  return new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN', minimumFractionDigits: 2 }).format(amount);
}

/** 0.1234 → "12.3 %" (las tasas de la API van de 0 a 1). */
export function formatRate(rate: number | null | undefined, decimals = 1): string {
  if (rate === null || rate === undefined) return '—';
  return `${(rate * 100).toFixed(decimals).replace('.', ',')} %`;
}

/** 1297 → "1 297" (separador de miles en español). */
export function formatInteger(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—';
  return new Intl.NumberFormat('es-PE').format(value);
}

export const ROLE_LABELS: Record<UserRole, string> = {
  ADMIN: 'Administrador',
  FRONT_DESK: 'Admisión',
  DOCTOR: 'Médico',
};

export function formatRole(role: UserRole): string {
  return ROLE_LABELS[role] ?? role;
}

/** "Carla Benavides Ortiz" → "CB" (para avatares con iniciales). */
export function initialsOf(fullName: string): string {
  const parts = fullName.trim().split(/[\s.]+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[1][0]).toUpperCase();
}

export const APPOINTMENT_STATUS_LABELS: Record<AppointmentStatus, string> = {
  BOOKED: 'Reservada',
  ATTENDED: 'Atendida',
  CANCELLED: 'Anulada',
  NO_SHOW: 'No asistió',
};

export const SLOT_STATUS_LABELS: Record<SlotStatus, string> = {
  FREE: 'Libre',
  BOOKED: 'Reservado',
  BLOCKED: 'Bloqueado',
};
