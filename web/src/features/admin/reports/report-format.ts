/**
 * Formato de porcentajes de los reportes con la misma configuración regional que `formatInteger`
 * (es-PE: coma de miles y punto decimal), para que "1,189" y "77.5%" no se contradigan en pantalla.
 */
const PERCENT_FORMAT = new Intl.NumberFormat('es-PE', { style: 'percent', minimumFractionDigits: 1, maximumFractionDigits: 1 });

/** Fracción de 0 a 1 → "77.5%". */
export function formatShare(fraction: number): string {
  return PERCENT_FORMAT.format(Number.isFinite(fraction) ? fraction : 0);
}

/** Parte sobre un total → "77.5%" (0 % si el total es cero). */
export function formatShareOf(value: number, total: number): string {
  return formatShare(total === 0 ? 0 : value / total);
}
