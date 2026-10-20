import { formatRate } from '@/lib/formatters';

/** Fracción de 0 a 1 → "77.5%" (mismo formato que el resto de la aplicación, ver `formatRate`). */
export function formatShare(fraction: number): string {
  return formatRate(fraction);
}

/** Parte sobre un total → "77.5%" (0.0% si el total es cero). */
export function formatShareOf(value: number, total: number): string {
  return formatShare(total === 0 ? 0 : value / total);
}
