import * as React from 'react';
import { RiShieldLine } from '@remixicon/react';
import type { ColumnDef } from '@tanstack/react-table';

import { DataTable } from '@/components/data-table';
import * as StatusBadge from '@/components/ui/status-badge';
import type { CoverageValidation } from '@/lib/api';
import { formatNumericDate } from '@/lib/formatters';

type CoverageTone = { status: 'completed' | 'pending' | 'failed' | 'disabled'; label: string };

/** Estado mostrable de una cobertura: vigente, suspendida o vencida (ACTIVE con validUntil pasado = vencida). */
function coverageTone(coverage: CoverageValidation): CoverageTone {
  if (coverage.valid) return { status: 'completed', label: 'Vigente' };
  if (coverage.coverageStatus === 'SUSPENDED') return { status: 'pending', label: 'Suspendida' };
  return { status: 'failed', label: 'Vencida' };
}

export function CoverageStatusBadge({ coverage }: { coverage: CoverageValidation }) {
  const tone = coverageTone(coverage);
  return (
    <StatusBadge.Root status={tone.status} variant='light'>
      <StatusBadge.Dot />
      {tone.label}
    </StatusBadge.Root>
  );
}

interface CoverageTableProps {
  coverages: CoverageValidation[] | undefined;
  isLoading?: boolean;
  errorMessage?: string | null;
  onRetry?: () => void;
}

/** Tabla de coberturas registradas del paciente (CU-017), válidas primero tal como las ordena la API. */
export function CoverageTable({ coverages, isLoading, errorMessage, onRetry }: CoverageTableProps) {
  const columns = React.useMemo<ColumnDef<CoverageValidation>[]>(
    () => [
      {
        id: 'insurer',
        header: 'Aseguradora y plan',
        cell: ({ row }) => (
          <div className='flex flex-col gap-0.5'>
            <span className='text-label-sm text-text-strong-950'>{row.original.insurer}</span>
            <span className='text-paragraph-xs text-text-sub-600'>{row.original.plan}</span>
          </div>
        ),
      },
      {
        id: 'policy',
        header: 'Póliza',
        cell: ({ row }) => <span className='text-paragraph-sm text-text-sub-600'>{row.original.policyNumber ?? '—'}</span>,
      },
      {
        id: 'status',
        header: 'Estado',
        cell: ({ row }) => <CoverageStatusBadge coverage={row.original} />,
      },
      {
        id: 'validUntil',
        header: 'Vigente hasta',
        cell: ({ row }) => (
          <span className='text-paragraph-sm text-text-sub-600'>{row.original.validUntil ? formatNumericDate(row.original.validUntil) : 'Sin fecha de fin'}</span>
        ),
      },
    ],
    [],
  );

  return (
    <DataTable
      columns={columns}
      data={coverages}
      isLoading={isLoading}
      errorMessage={errorMessage}
      onRetry={onRetry}
      emptyIcon={RiShieldLine}
      emptyTitle='Sin seguros registrados'
      emptyDescription='El paciente se atiende como particular. Puedes asociarle un seguro más abajo.'
      skeletonRows={2}
      getRowId={(row) => String(row.patientInsuranceId)}
    />
  );
}
