import * as React from 'react';
import {
  RiCalendarScheduleLine,
  RiCheckLine,
  RiErrorWarningLine,
  RiPhoneLine,
  type RemixiconComponentType,
} from '@remixicon/react';
import { flexRender, getCoreRowModel, useReactTable, type ColumnDef } from '@tanstack/react-table';

import { AppointmentStatusBadge } from '@/components/appointment-status-badge';
import { EmptyState } from '@/components/empty-state';
import { Skeleton } from '@/components/loading-state';
import * as Badge from '@/components/ui/badge';
import * as Button from '@/components/ui/button';
import * as Table from '@/components/ui/table';
import * as Tooltip from '@/components/ui/tooltip';
import type { AgendaItem, AttendanceStatus, BookingChannel } from '@/lib/api/types';
import { cn } from '@/lib/cn';
import { formatDocument, formatTime } from '@/lib/formatters';

const CHANNEL_BADGE: Record<BookingChannel, { label: string; color: 'purple' | 'blue' }> = {
  KIOSK: { label: 'Kiosco', color: 'purple' },
  FRONT_DESK: { label: 'Ventanilla', color: 'blue' },
};

/** Columnas que no se imprimen (acciones). */
const PRINT_HIDDEN_COLUMNS = new Set(['actions']);

const SKELETON_ROWS = 5;

export interface AgendaTableProps {
  items: AgendaItem[] | undefined;
  isLoading: boolean;
  /** Mensaje de error ya en español (getErrorMessage). */
  errorMessage?: string | null;
  onRetry?: () => void;
  /** Cita resaltada como próxima pendiente. */
  highlightedId: number | null;
  /** true cuando la fecha de la agenda es hoy o anterior (la API solo admite registrar asistencia entonces). */
  canRegister: boolean;
  onRegister: (item: AgendaItem, status: AttendanceStatus) => void;
  emptyTitle: string;
  emptyDescription?: React.ReactNode;
  emptyIcon?: RemixiconComponentType;
}

/** Celda de seguro: "Rímac Seguros · Red Médica" → aseguradora y plan; "Particular" en una sola línea. */
function InsuranceCell({ insurance }: { insurance: string }) {
  const value = insurance.trim();
  if (value === '' || value.toLowerCase() === 'particular') {
    return (
      <div className='flex flex-col gap-0.5'>
        <span className='text-label-sm text-text-strong-950'>Particular</span>
        <span className='text-paragraph-xs text-text-sub-600'>Sin seguro</span>
      </div>
    );
  }
  const [insurer, ...rest] = value.split(' · ');
  return (
    <div className='flex flex-col gap-0.5'>
      <span className='text-label-sm text-text-strong-950'>{insurer}</span>
      {rest.length > 0 && <span className='text-paragraph-xs text-text-sub-600'>{rest.join(' · ')}</span>}
    </div>
  );
}

function AttendanceButtons({
  item,
  disabled,
  onRegister,
}: {
  item: AgendaItem;
  disabled: boolean;
  onRegister: AgendaTableProps['onRegister'];
}) {
  return (
    <>
      <Button.Root
        variant='primary'
        mode='filled'
        size='small'
        disabled={disabled}
        onClick={() => onRegister(item, 'ATTENDED')}
        aria-label={`Marcar como atendido a ${item.patient.fullName}`}
      >
        <Button.Icon as={RiCheckLine} />
        Atendido
      </Button.Root>
      <Button.Root
        variant='neutral'
        mode='stroke'
        size='small'
        disabled={disabled}
        onClick={() => onRegister(item, 'NO_SHOW')}
        aria-label={`Marcar que ${item.patient.fullName} no asistió`}
      >
        No asistió
      </Button.Root>
    </>
  );
}

function ActionsCell({ item, canRegister, onRegister }: { item: AgendaItem; canRegister: boolean; onRegister: AgendaTableProps['onRegister'] }) {
  if (item.status === 'BOOKED') {
    if (canRegister) {
      return (
        <div className='flex flex-wrap items-center justify-end gap-1.5'>
          <AttendanceButtons item={item} disabled={false} onRegister={onRegister} />
        </div>
      );
    }
    return (
      <Tooltip.Root>
        <Tooltip.Trigger asChild>
          <div tabIndex={0} className='flex flex-wrap items-center justify-end gap-1.5 outline-none' aria-label='Registro de asistencia no disponible todavía'>
            <AttendanceButtons item={item} disabled onRegister={onRegister} />
          </div>
        </Tooltip.Trigger>
        <Tooltip.Content side='left'>Podrás registrar la asistencia el día de la atención</Tooltip.Content>
      </Tooltip.Root>
    );
  }
  if (item.attendanceRegisteredAt) {
    return (
      <div className='flex flex-col items-end gap-0.5 text-right'>
        <span className='text-label-sm text-text-strong-950'>{formatTime(item.attendanceRegisteredAt)}</span>
        <span className='text-paragraph-xs text-text-sub-600'>Hora de registro</span>
      </div>
    );
  }
  return <span className='block text-right text-paragraph-xs text-text-soft-400'>—</span>;
}

function buildColumns(highlightedId: number | null, canRegister: boolean, onRegister: AgendaTableProps['onRegister']): ColumnDef<AgendaItem>[] {
  return [
    {
      id: 'time',
      accessorKey: 'startTime',
      header: () => <span className='whitespace-nowrap'>Hora y consultorio</span>,
      cell: ({ row }) => {
        const item = row.original;
        return (
          <div className='flex flex-col items-start gap-0.5 whitespace-nowrap'>
            <span className='text-label-sm text-text-strong-950'>
              {formatTime(item.startTime)} – {formatTime(item.endTime)}
            </span>
            <span className='text-paragraph-xs text-text-sub-600'>{item.room ?? 'Sin consultorio asignado'}</span>
            <span className='text-paragraph-xs text-text-sub-600'>{item.location}</span>
            {item.appointmentId === highlightedId && (
              <Badge.Root variant='lighter' color='blue' size='small' className='mt-1'>
                Siguiente
              </Badge.Root>
            )}
          </div>
        );
      },
    },
    {
      id: 'patient',
      accessorKey: 'patient.fullName',
      header: 'Paciente',
      cell: ({ row }) => {
        const { patient } = row.original;
        return (
          <div className='flex min-w-36 flex-col gap-0.5'>
            <span className='text-label-sm text-text-strong-950'>{patient.fullName}</span>
            <span className='text-paragraph-xs text-text-sub-600'>{formatDocument(patient.documentType, patient.documentNumber)}</span>
            <span className='flex items-center gap-1 text-paragraph-xs text-text-sub-600'>
              <RiPhoneLine className='size-3.5 shrink-0 text-text-soft-400' aria-hidden />
              {patient.phone}
            </span>
          </div>
        );
      },
    },
    {
      id: 'reason',
      accessorKey: 'visitReason',
      header: () => <span className='whitespace-nowrap'>Motivo de consulta</span>,
      cell: ({ row }) => (
        <p className='line-clamp-3 min-w-36 text-paragraph-sm text-text-sub-600' title={row.original.visitReason}>
          {row.original.visitReason}
        </p>
      ),
    },
    {
      id: 'insurance',
      accessorKey: 'insurance',
      header: 'Seguro',
      cell: ({ row }) => <InsuranceCell insurance={row.original.insurance} />,
    },
    {
      id: 'channel',
      accessorKey: 'bookingChannel',
      header: 'Canal',
      cell: ({ row }) => {
        const channel = CHANNEL_BADGE[row.original.bookingChannel];
        return (
          <Badge.Root variant='light' color={channel.color} size='medium'>
            <Badge.Dot />
            {channel.label}
          </Badge.Root>
        );
      },
    },
    {
      id: 'status',
      accessorKey: 'status',
      header: 'Estado',
      cell: ({ row }) => <AppointmentStatusBadge status={row.original.status} />,
    },
    {
      id: 'actions',
      header: () => <span className='block text-right'>Asistencia</span>,
      cell: ({ row }) => <ActionsCell item={row.original} canRegister={canRegister} onRegister={onRegister} />,
    },
  ];
}

/**
 * Tabla de la agenda según data-table-demo (Table AlignUI + @tanstack/react-table, RowDivider entre filas).
 * Es local a la feature porque necesita resaltar la fila de la próxima cita pendiente.
 */
export function AgendaTable({
  items,
  isLoading,
  errorMessage,
  onRetry,
  highlightedId,
  canRegister,
  onRegister,
  emptyTitle,
  emptyDescription,
  emptyIcon = RiCalendarScheduleLine,
}: AgendaTableProps) {
  const columns = React.useMemo(() => buildColumns(highlightedId, canRegister, onRegister), [highlightedId, canRegister, onRegister]);
  const data = React.useMemo(() => items ?? [], [items]);

  const table = useReactTable({
    data,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getRowId: (row) => String(row.appointmentId),
  });

  const columnCount = columns.length;
  const rows = table.getRowModel().rows;

  return (
    <Table.Root>
      <Table.Header>
        {table.getHeaderGroups().map((headerGroup) => (
          <Table.Row key={headerGroup.id}>
            {headerGroup.headers.map((header) => (
              <Table.Head key={header.id} className={cn(PRINT_HIDDEN_COLUMNS.has(header.column.id) && 'print:hidden')}>
                {header.isPlaceholder ? null : flexRender(header.column.columnDef.header, header.getContext())}
              </Table.Head>
            ))}
          </Table.Row>
        ))}
      </Table.Header>
      <Table.Body>
        {isLoading ? (
          Array.from({ length: SKELETON_ROWS }).map((_, index) => (
            <React.Fragment key={`skeleton-${index}`}>
              <Table.Row>
                {Array.from({ length: columnCount }).map((__, cellIndex) => (
                  <Table.Cell key={cellIndex}>
                    <Skeleton className={cn('h-4', cellIndex === 0 ? 'w-3/4' : 'w-1/2')} />
                  </Table.Cell>
                ))}
              </Table.Row>
              {index < SKELETON_ROWS - 1 && <Table.RowDivider />}
            </React.Fragment>
          ))
        ) : errorMessage ? (
          <Table.Row>
            <Table.Cell colSpan={columnCount}>
              <EmptyState
                size='small'
                icon={RiErrorWarningLine}
                title='No pudimos cargar tu agenda'
                description={errorMessage}
                action={
                  onRetry && (
                    <Button.Root variant='neutral' mode='stroke' size='xsmall' onClick={onRetry}>
                      Reintentar
                    </Button.Root>
                  )
                }
              />
            </Table.Cell>
          </Table.Row>
        ) : rows.length === 0 ? (
          <Table.Row>
            <Table.Cell colSpan={columnCount}>
              <EmptyState icon={emptyIcon} title={emptyTitle} description={emptyDescription} />
            </Table.Cell>
          </Table.Row>
        ) : (
          rows.map((row, index, all) => {
            const highlighted = row.original.appointmentId === highlightedId;
            return (
              <React.Fragment key={row.id}>
                <Table.Row data-highlighted={highlighted ? 'true' : undefined} aria-current={highlighted ? 'true' : undefined}>
                  {row.getVisibleCells().map((cell) => (
                    <Table.Cell
                      key={cell.id}
                      className={cn(
                        'py-3 align-top',
                        highlighted && 'bg-primary-alpha-10',
                        PRINT_HIDDEN_COLUMNS.has(cell.column.id) && 'print:hidden',
                      )}
                    >
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </Table.Cell>
                  ))}
                </Table.Row>
                {index < all.length - 1 && <Table.RowDivider />}
              </React.Fragment>
            );
          })
        )}
      </Table.Body>
    </Table.Root>
  );
}
