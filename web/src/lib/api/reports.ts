import { downloadBlob, http, type BlobResult } from '@/lib/api/client';
import type {
  AppointmentsReport,
  AppointmentsReportQuery,
  NoShowRateReport,
  OccupancyReport,
  ReportKind,
  ReportRange,
} from '@/lib/api/types';

/** Reportes (sección 10). Solo rol ADMIN. Rango máximo 366 días. */

/** GET /admin/reports/appointments — totales por estado, por especialidad y por sede. */
export function getAppointmentsReport(query: AppointmentsReportQuery): Promise<AppointmentsReport> {
  return http.get<AppointmentsReport>('/admin/reports/appointments', { ...query });
}

/** GET /admin/reports/no-show-rate — tasa de inasistencia por especialidad. */
export function getNoShowRateReport(range: ReportRange): Promise<NoShowRateReport> {
  return http.get<NoShowRateReport>('/admin/reports/no-show-rate', { ...range });
}

/** GET /admin/reports/occupancy — ocupación de cupos por especialidad y sede. */
export function getOccupancyReport(range: ReportRange): Promise<OccupancyReport> {
  return http.get<OccupancyReport>('/admin/reports/occupancy', { ...range });
}

const CSV_PREFIX: Record<ReportKind, string> = {
  appointments: 'reporte-citas',
  'no-show-rate': 'reporte-inasistencia',
  occupancy: 'reporte-ocupacion',
};

/** GET /admin/reports/export — CSV (UTF-8 con BOM). Devuelve el Blob y el nombre sugerido. */
export function exportCsv(report: ReportKind, range: ReportRange): Promise<BlobResult> {
  return http.blob('/admin/reports/export', { report, ...range });
}

/** Descarga el CSV directamente en el navegador. */
export async function downloadCsv(report: ReportKind, range: ReportRange): Promise<void> {
  const result = await exportCsv(report, range);
  downloadBlob(result, `${CSV_PREFIX[report]}_${range.from}_${range.to}.csv`);
}

export const reportsApi = {
  getAppointmentsReport,
  getNoShowRateReport,
  getOccupancyReport,
  exportCsv,
  downloadCsv,
};
