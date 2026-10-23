import * as React from 'react';
import { RiTimeLine } from '@remixicon/react';
import { useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';

import { Card, CardHeader } from '@/components/card';
import { DataTable } from '@/components/data-table';
import { EmptyState } from '@/components/empty-state';
import { LoadingState } from '@/components/loading-state';
import { ExportCsvButton } from '@/features/admin/reports/components/export-csv-button';
import { RateBar } from '@/features/admin/reports/components/rate-bar';
import { ReportError } from '@/features/admin/reports/components/report-error';
import { occupancyColor } from '@/features/admin/reports/report-range';
import { reportsApi, type OccupancyRow, type ReportRange } from '@/lib/api';
import { formatInteger } from '@/lib/formatters';

function Num({ value }: { value: number }) {
  return <span className='text-paragraph-sm tabular-nums text-text-sub-600'>{formatInteger(value)}</span>;
}

/** Pestaña "Ocupación" (RF-35): cupos ocupados sobre cupos generados, por especialidad y sede. */
export function OccupancyTab({ range }: { range: ReportRange }) {
  const query = useQuery({ queryKey: ['reports', 'occupancy', range], queryFn: () => reportsApi.getOccupancyReport(range) });

  const columns = React.useMemo<ColumnDef<OccupancyRow>[]>(
    () => [
      { id: 'specialty', header: 'Especialidad', cell: ({ row }) => <span className='text-label-sm text-text-strong-950'>{row.original.specialtyName}</span> },
      { id: 'location', header: 'Sede', size: 170, cell: ({ row }) => <span className='text-paragraph-sm text-text-sub-600'>{row.original.locationName}</span> },
      { id: 'slotsTotal', header: 'Cupos', size: 100, cell: ({ row }) => <Num value={row.original.slotsTotal} /> },
      { id: 'slotsBlocked', header: 'Bloqueados', size: 110, cell: ({ row }) => <Num value={row.original.slotsBlocked} /> },
      { id: 'slotsBooked', header: 'Ocupados', size: 100, cell: ({ row }) => <Num value={row.original.slotsBooked} /> },
      { id: 'rate', header: 'Ocupación', size: 240, cell: ({ row }) => <RateBar rate={row.original.rate} color={occupancyColor(row.original.rate)} /> },
    ],
    [],
  );

  if (query.isPending) {
    return (
      <Card>
        <LoadingState rows={5} label='Cargando el reporte de ocupación…' />
      </Card>
    );
  }
  if (query.isError) return <ReportError error={query.error} onRetry={() => void query.refetch()} />;

  const report = query.data;
  const exportButton = <ExportCsvButton kind='occupancy' range={range} disabled={report.total.slotsTotal === 0} />;

  if (report.total.slotsTotal === 0) {
    return (
      <Card>
        <CardHeader title='Ocupación de cupos' actions={exportButton} />
        <EmptyState icon={RiTimeLine} title='No hay cupos generados en este periodo' description='Genera agendas para el periodo o prueba con otro rango de fechas.' />
      </Card>
    );
  }

  return (
    <div className='flex flex-col gap-6'>
      <Card className='flex flex-col gap-4'>
        <CardHeader title='Total general' description='Cupos ocupados (reservados, atendidos o con inasistencia) sobre los cupos generados, sin contar los bloqueados.' actions={exportButton} />
        <div className='grid grid-cols-[1fr_1fr_1fr_2fr] items-center gap-6 rounded-10 bg-bg-weak-50 p-4'>
          <div className='flex flex-col'>
            <span className='text-paragraph-sm text-text-sub-600'>Cupos</span>
            <span className='text-title-h5 text-text-strong-950'>{formatInteger(report.total.slotsTotal)}</span>
          </div>
          <div className='flex flex-col'>
            <span className='text-paragraph-sm text-text-sub-600'>Bloqueados</span>
            <span className='text-title-h5 text-text-strong-950'>{formatInteger(report.total.slotsBlocked)}</span>
          </div>
          <div className='flex flex-col'>
            <span className='text-paragraph-sm text-text-sub-600'>Ocupados</span>
            <span className='text-title-h5 text-text-strong-950'>{formatInteger(report.total.slotsBooked)}</span>
          </div>
          <div className='flex flex-col gap-2'>
            <span className='text-paragraph-sm text-text-sub-600'>Ocupación general</span>
            <RateBar rate={report.total.rate} color={occupancyColor(report.total.rate)} size='large' />
          </div>
        </div>
      </Card>

      <Card className='flex flex-col gap-4'>
        <CardHeader title='Por especialidad y sede' description='Verde: 70% o más. Naranja: entre 40% y 70%. Rojo: menos de 40%.' />
        <DataTable columns={columns} data={report.rows} emptyTitle='Sin datos de ocupación' getRowId={(row) => `${row.specialtyId}-${row.locationId}`} />
      </Card>
    </div>
  );
}
