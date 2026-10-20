import { differenceInCalendarDays, endOfMonth, isValid, parse, startOfMonth } from 'date-fns';

import type { ReportRange } from '@/lib/api';
import { toIsoDate } from '@/lib/formatters';

/** Días máximos que acepta la API en un reporte (contrato §8). */
export const MAX_RANGE_DAYS = 366;

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

function parseIso(value: string): Date {
  return parse(value, 'yyyy-MM-dd', new Date());
}

/** "yyyy-MM-dd" con una fecha que existe en el calendario ("2026-13-45" o "2026-02-30" no lo son). */
export function isIsoDate(value: string | null | undefined): value is string {
  return value !== null && value !== undefined && ISO_DATE.test(value) && isValid(parseIso(value));
}

/** Rango por defecto: el mes actual completo. */
export function currentMonthRange(): ReportRange {
  const now = new Date();
  return { from: toIsoDate(startOfMonth(now)), to: toIsoDate(endOfMonth(now)) };
}

/** Valida el rango en el cliente con los mismos mensajes que devuelve la API. */
export function validateRange(range: ReportRange): string | null {
  if (!isIsoDate(range.from) || !isIsoDate(range.to)) return 'Ingresa las dos fechas del periodo.';
  if (range.from > range.to) return 'La fecha final debe ser igual o posterior a la fecha inicial.';
  if (differenceInCalendarDays(parseIso(range.to), parseIso(range.from)) + 1 > MAX_RANGE_DAYS) {
    return `El reporte puede abarcar como máximo ${MAX_RANGE_DAYS} días.`;
  }
  return null;
}

/** Tasa (0 a 1) → porcentaje entero (0 a 100) para barras y gráficos. */
export function toPercent(rate: number): number {
  return Math.round(Math.min(Math.max(rate, 0), 1) * 100);
}

/** Color de la barra de progreso AlignUI según umbral. */
export type BarColor = 'green' | 'orange' | 'red' | 'blue';
/** Umbrales de inasistencia: menos de 10 % bien, menos de 20 % alerta, el resto crítico. */
export function noShowColor(rate: number): BarColor {
  if (rate < 0.1) return 'green';
  if (rate < 0.2) return 'orange';
  return 'red';
}

/** Umbrales de ocupación (a más ocupación, mejor): 70 % o más bien, 40 % o más alerta, el resto crítico. */
export function occupancyColor(rate: number): BarColor {
  if (rate >= 0.7) return 'green';
  if (rate >= 0.4) return 'orange';
  return 'red';
}
