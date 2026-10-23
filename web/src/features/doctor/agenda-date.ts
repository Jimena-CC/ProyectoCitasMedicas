import { addDays, format, isValid, parse } from 'date-fns';

import type { AgendaItem, IsoDate } from '@/lib/api/types';
import { todayIso } from '@/lib/formatters';

const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

/** "2026-10-20" → Date local (sin desfase de zona horaria). */
export function parseIsoDate(value: IsoDate): Date {
  return parse(value, 'yyyy-MM-dd', new Date());
}

/**
 * Normaliza el parámetro `?fecha=` de la agenda: si falta o no es una fecha válida se usa hoy.
 */
export function resolveAgendaDate(param: string | null): IsoDate {
  if (!param || !ISO_DATE_PATTERN.test(param)) return todayIso();
  return isValid(parseIsoDate(param)) ? param : todayIso();
}

/** Desplaza una fecha ISO n días (negativo hacia atrás). */
export function shiftIsoDate(value: IsoDate, days: number): IsoDate {
  return format(addDays(parseIsoDate(value), days), 'yyyy-MM-dd');
}

/** Solo se puede registrar la asistencia de citas de hoy o de días anteriores (regla de la API). */
export function canRegisterAttendance(date: IsoDate): boolean {
  return date <= todayIso();
}

/**
 * Próxima cita pendiente de la agenda de hoy: la primera BOOKED que aún no termina o, si todas pasaron,
 * la primera BOOKED del día. En otras fechas no hay "siguiente" y no se resalta ninguna.
 */
export function findNextPending(items: AgendaItem[], date: IsoDate): number | null {
  if (date !== todayIso()) return null;
  const booked = items.filter((item) => item.status === 'BOOKED');
  if (booked.length === 0) return null;
  const now = format(new Date(), 'HH:mm');
  const upcoming = booked.find((item) => item.endTime >= now);
  return (upcoming ?? booked[0]).appointmentId;
}
