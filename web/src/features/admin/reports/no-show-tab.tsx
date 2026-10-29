import * as React from 'react';
import { RiUserUnfollowLine } from '@remixicon/react';
import { useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';

import { Card, CardHeader } from '@/components/card';
import { DataTable } from '@/components/data-table';
import { EmptyState } from '@/components/empty-state';
import { LoadingState } from '@/components/loading-state';
import { ExportCsvButton } from '@/features/admin/reports/components/export-csv-button';
import { ExportXlsxButton } from '@/features/admin/reports/components/export-xlsx-button';
import { RateBar } from '@/features/admin/reports/components/rate-bar';
import { ReportError } from '@/features/admin/reports/components/report-error';
import { noShowColor } from '@/features/admin/reports/report-range';
import { reportsApi, type NoShowRow, type ReportRange } from '@/lib/api';
import { formatInteger } from '@/lib/formatters';

/** Pestaña "Inasistencia" (RF-34): tasa NO_SHOW / (ATTENDED + NO_SHOW) por especialidad y total general. */
export function NoShowTab({ range }: { range: ReportRange }) {
  const query = useQuery({ queryKey: ['reports', 'no-show-rate', range], queryFn: () => reportsApi.getNoShowRateReport(range) });

  const columns = React.useMemo<ColumnDef<NoShowRow>[]>(
    () => [
      { id: 'name', header: 'Especialidad', cell: ({ row }) => <span className='text-label-sm text-text-strong-950'>{row.original.specialtyName}</span> },
      {
        id: 'attended',
        header: 'Atendidas',
        size: 130,
        cell: ({ row }) => <span className='text-paragraph-sm tabular-nums text-text-sub-600'>{formatInteger(row.original.attended)}</span>,
      },
      {
        id: 'noShow',
        header: 'No asistieron',
        size: 130,
        cell: ({ row }) => <span className='text-paragraph-sm tabular-nums text-text-sub-600'>{formatInteger(row.original.noShow)}</span>,
      },
      {
        id: 'rate',
        header: 'Tasa de inasistencia',
        size: 280,
        cell: ({ row }) => <RateBar rate={row.original.rate} color={noShowColor(row.original.rate)} />,
      },
    ],
    [],
  );

  if (query.isPending) {
    return (
      <Card>
        <LoadingState rows={5} label='Cargando el reporte de inasistencia…' />
      </Card>
    );
  }
  if (query.isError) return <ReportError error={query.error} onRetry={() => void query.refetch()} />;

  const report = query.data;
  const registered = report.total.attended + report.total.noShow;
  const exportButton = (
    <div className='flex flex-wrap items-center gap-2'>
      <ExportCsvButton kind='no-show-rate' range={range} disabled={registered === 0} />
      <ExportXlsxButton kind='no-show-rate' range={range} disabled={registered === 0} />
    </div>
  );

  if (registered === 0) {
    return (
      <Card>
        <CardHeader title='Tasa de inasistencia' actions={exportButton} />
        <EmptyState
          icon={RiUserUnfollowLine}
          title='No hay asistencias registradas en este periodo'
          description='La tasa se calcula con las citas atendidas y las inasistencias. Prueba con otro rango de fechas.'
        />
      </Card>
    );
  }

  const color = noShowColor(report.total.rate);

  return (
    <div className='flex flex-col gap-6'>
      <Card className='flex flex-col gap-4'>
        <CardHeader
          title='Total general'
          description='Inasistencias sobre el total de citas con asistencia registrada (atendidas más inasistencias).'
          actions={exportButton}
        />
        <div className='grid grid-cols-[1fr_1fr_2fr] items-center gap-6 rounded-10 bg-bg-weak-50 p-4'>
          <div className='flex flex-col'>
            <span className='text-paragraph-sm text-text-sub-600'>Atendidas</span>
            <span className='text-title-h5 text-text-strong-950'>{formatInteger(report.total.attended)}</span>
          </div>
          <div className='flex flex-col'>
            <span className='text-paragraph-sm text-text-sub-600'>No asistieron</span>
            <span className='text-title-h5 text-text-strong-950'>{formatInteger(report.total.noShow)}</span>
          </div>
          <div className='flex flex-col gap-2'>
            <span className='text-paragraph-sm text-text-sub-600'>Tasa de inasistencia general</span>
            <RateBar rate={report.total.rate} color={color} size='large' />
          </div>
        </div>
      </Card>

      <Card className='flex flex-col gap-4'>
        <CardHeader title='Por especialidad' description='Verde: menos de 10%. Naranja: menos de 20%. Rojo: 20% o más.' />
        <DataTable columns={columns} data={report.bySpecialty} emptyTitle='Sin datos por especialidad' getRowId={(row) => String(row.specialtyId)} />
      </Card>
    </div>
  );
}
