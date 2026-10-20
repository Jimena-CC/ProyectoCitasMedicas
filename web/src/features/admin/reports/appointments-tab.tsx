import * as React from 'react';
import {
  RiBarChartBoxLine,
  RiCalendarCheckLine,
  RiCalendarCloseLine,
  RiCalendarLine,
  RiCheckboxCircleLine,
  RiUserUnfollowLine,
} from '@remixicon/react';
import { useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';

import { Card, CardHeader } from '@/components/card';
import { DataTable } from '@/components/data-table';
import { EmptyState } from '@/components/empty-state';
import { LoadingState } from '@/components/loading-state';
import { ExportCsvButton } from '@/features/admin/reports/components/export-csv-button';
import { ReportError } from '@/features/admin/reports/components/report-error';
import { StatCard } from '@/features/admin/reports/components/stat-card';
import { StatusBarChart } from '@/features/admin/reports/components/status-bar-chart';
import type { ReportFilterValues } from '@/features/admin/reports/components/report-filters';
import { reportsApi, type LocationStatusRow, type SpecialtyStatusRow, type StatusTotals } from '@/lib/api';
import { formatInteger } from '@/lib/formatters';

function percentCaption(value: number, total: number): string {
  if (total === 0) return 'Sin citas';
  return `${((value / total) * 100).toFixed(1).replace('.', ',')} % del total`;
}

function Num({ value, strong = false }: { value: number; strong?: boolean }) {
  return <span className={strong ? 'text-label-sm tabular-nums text-text-strong-950' : 'text-paragraph-sm tabular-nums text-text-sub-600'}>{formatInteger(value)}</span>;
}

/** Columnas numéricas comunes a los desgloses por especialidad y por sede. */
function totalsColumns<T extends { totals: StatusTotals }>(): ColumnDef<T>[] {
  return [
    { id: 'booked', header: 'Reservadas', size: 120, cell: ({ row }) => <Num value={row.original.totals.booked} /> },
    { id: 'attended', header: 'Atendidas', size: 120, cell: ({ row }) => <Num value={row.original.totals.attended} /> },
    { id: 'cancelled', header: 'Anuladas', size: 120, cell: ({ row }) => <Num value={row.original.totals.cancelled} /> },
    { id: 'noShow', header: 'No asistieron', size: 130, cell: ({ row }) => <Num value={row.original.totals.noShow} /> },
    { id: 'total', header: 'Total', size: 100, cell: ({ row }) => <Num value={row.original.totals.total} strong /> },
  ];
}

/** Pestaña "Citas por estado" (RF-33): totales, gráfico de barras y desgloses por especialidad y sede. */
export function AppointmentsTab({ filters }: { filters: ReportFilterValues }) {
  const range = { from: filters.from, to: filters.to };
  const query = useQuery({
    queryKey: ['reports', 'appointments', filters],
    queryFn: () => reportsApi.getAppointmentsReport({ ...range, specialtyId: filters.specialtyId, locationId: filters.locationId }),
  });

  const specialtyColumns = React.useMemo<ColumnDef<SpecialtyStatusRow>[]>(
    () => [
      { id: 'name', header: 'Especialidad', cell: ({ row }) => <span className='text-label-sm text-text-strong-950'>{row.original.specialtyName}</span> },
      ...totalsColumns<SpecialtyStatusRow>(),
    ],
    [],
  );
  const locationColumns = React.useMemo<ColumnDef<LocationStatusRow>[]>(
    () => [
      { id: 'name', header: 'Sede', cell: ({ row }) => <span className='text-label-sm text-text-strong-950'>{row.original.locationName}</span> },
      ...totalsColumns<LocationStatusRow>(),
    ],
    [],
  );

  if (query.isPending) {
    return (
      <Card>
        <LoadingState rows={5} label='Cargando el reporte de citas…' />
      </Card>
    );
  }
  if (query.isError) return <ReportError error={query.error} onRetry={() => void query.refetch()} />;

  const report = query.data;
  const { totals } = report;
  const exportButton = <ExportCsvButton kind='appointments' range={range} disabled={totals.total === 0} />;

  if (totals.total === 0) {
    return (
      <Card>
        <CardHeader title='Citas por estado' actions={exportButton} />
        <EmptyState
          icon={RiBarChartBoxLine}
          title='No hay citas en este periodo'
          description='Prueba con otro rango de fechas o quita el filtro de especialidad o sede.'
        />
      </Card>
    );
  }

  return (
    <div className='flex flex-col gap-6'>
      <div className='grid grid-cols-5 gap-4'>
        <StatCard label='Reservadas' value={totals.booked} caption={percentCaption(totals.booked, totals.total)} icon={RiCalendarLine} tone='warning' />
        <StatCard label='Atendidas' value={totals.attended} caption={percentCaption(totals.attended, totals.total)} icon={RiCheckboxCircleLine} tone='success' />
        <StatCard label='Anuladas' value={totals.cancelled} caption={percentCaption(totals.cancelled, totals.total)} icon={RiCalendarCloseLine} tone='error' />
        <StatCard label='No asistieron' value={totals.noShow} caption={percentCaption(totals.noShow, totals.total)} icon={RiUserUnfollowLine} tone='faded' />
        <StatCard label='Total de citas' value={totals.total} caption='En el periodo' icon={RiCalendarCheckLine} tone='primary' highlighted />
      </div>

      <Card className='flex flex-col gap-5'>
        <CardHeader title='Distribución por estado' description='Cantidad de citas y porcentaje sobre el total del periodo.' actions={exportButton} />
        <StatusBarChart totals={totals} />
      </Card>

      <Card className='flex flex-col gap-4'>
        <CardHeader title='Por especialidad' description='Citas del periodo agrupadas por especialidad.' />
        <DataTable columns={specialtyColumns} data={report.bySpecialty} emptyTitle='Sin citas por especialidad' getRowId={(row) => String(row.specialtyId)} />
      </Card>
      <Card className='flex flex-col gap-4'>
        <CardHeader title='Por sede' description='Citas del periodo agrupadas por sede.' />
        <DataTable columns={locationColumns} data={report.byLocation} emptyTitle='Sin citas por sede' getRowId={(row) => String(row.locationId)} />
      </Card>
    </div>
  );
}
