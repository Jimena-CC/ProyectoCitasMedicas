import type { AppointmentsReport, ReportRange, StatusTotals } from '@/lib/api';

/**
 * CSV del reporte de citas generado en el navegador, con el mismo formato que GET /admin/reports/export
 * (UTF-8 con BOM, separador coma, saltos CRLF). Se usa cuando hay filtro de especialidad o sede, porque
 * la exportación de la API siempre incluye todas (RF-36: el CSV debe respetar los filtros de la pantalla).
 */

const BOM = '﻿';
const LINE_SEPARATOR = '\r\n';
const HEADER = ['grupo', 'nombre', 'reservadas', 'atendidas', 'anuladas', 'inasistencias', 'total'];

function escapeCell(value: string): string {
  return /[",\r\n]/.test(value) ? `"${value.replaceAll('"', '""')}"` : value;
}

function totalsRow(group: string, name: string, totals: StatusTotals): string[] {
  return [group, name, ...[totals.booked, totals.attended, totals.cancelled, totals.noShow, totals.total].map(String)];
}

export function appointmentsCsv(report: AppointmentsReport, totalName: string): string {
  const rows: string[][] = [
    HEADER,
    ...report.bySpecialty.map((row) => totalsRow('Especialidad', row.specialtyName, row.totals)),
    ...report.byLocation.map((row) => totalsRow('Sede', row.locationName, row.totals)),
    totalsRow('Total', totalName, report.totals),
  ];
  return BOM + rows.map((row) => row.map(escapeCell).join(',')).join(LINE_SEPARATOR) + LINE_SEPARATOR;
}

/** "Medicina General" → "medicina-general" (sin tildes ni espacios, para nombres de archivo). */
function slug(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

/** reporte-citas_2026-09-21_2026-10-20_cardiologia_sede-centro.csv */
export function appointmentsCsvFilename(range: ReportRange, filterNames: string[]): string {
  const suffix = filterNames.map(slug).filter(Boolean).join('_');
  return `reporte-citas_${range.from}_${range.to}${suffix ? `_${suffix}` : ''}.csv`;
}
