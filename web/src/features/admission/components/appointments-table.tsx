import * as React from 'react';
import { RiCalendarLine, RiCalendarScheduleLine, RiCloseCircleLine, RiMore2Line } from '@remixicon/react';
import type { ColumnDef } from '@tanstack/react-table';

import { AppointmentStatusBadge } from '@/components/appointment-status-badge';
import { DataTable, SortableHeader } from '@/components/data-table';
import * as Button from '@/components/ui/button';
import * as Dropdown from '@/components/ui/dropdown';
import type { Appointment } from '@/lib/api';
import { formatShortDate, formatTime } from '@/lib/formatters';

import { isAppointmentActionable } from '../lib/patients';

interface AppointmentsTableProps {
  appointments: Appointment[] | undefined;
  isLoading?: boolean;
  errorMessage?: string | null;
  onRetry?: () => void;
  onReschedule: (appointment: Appointment) => void;
  onCancel: (appointment: Appointment) => void;
  emptyTitle?: string;
  emptyDescription?: React.ReactNode;
  emptyAction?: React.ReactNode;
}

/**
 * Citas del paciente (CU-019/CU-020) al estilo data-table-demo: celdas de una o dos líneas y una sola acción
 * icónica por fila (RiMore2Line) que abre el menú con Reprogramar y Anular (dropdown-demo). Solo las citas
 * reservadas y futuras muestran ese menú.
 */
export function AppointmentsTable({
  appointments,
  isLoading,
  errorMessage,
  onRetry,
  onReschedule,
  onCancel,
  emptyTitle = 'El paciente no tiene citas',
  emptyDescription,
  emptyAction,
}: AppointmentsTableProps) {
  const columns = React.useMemo<ColumnDef<Appointment>[]>(
    () => [
      {
        id: 'code',
        accessorKey: 'code',
        header: 'Código',
        cell: ({ row }) => <span className='whitespace-nowrap text-label-sm text-text-strong-950'>{row.original.code}</span>,
      },
      {
        id: 'date',
        accessorFn: (row) => `${row.date} ${row.startTime}`,
        header: ({ column }) => (
          <SortableHeader column={column}>
            <span className='whitespace-nowrap'>Fecha y hora</span>
          </SortableHeader>
        ),
        cell: ({ row }) => (
          <div className='flex flex-col gap-0.5 whitespace-nowrap'>
            <span className='text-label-sm text-text-strong-950'>{formatShortDate(row.original.date)}</span>
            <span className='text-paragraph-xs text-text-sub-600'>
              {formatTime(row.original.startTime)} – {formatTime(row.original.endTime)}
            </span>
          </div>
        ),
      },
      {
        id: 'specialty',
        accessorKey: 'specialty',
        header: ({ column }) => <SortableHeader column={column}>Especialidad y médico</SortableHeader>,
        cell: ({ row }) => (
          <div className='flex flex-col gap-0.5'>
            <span className='text-label-sm text-text-strong-950'>{row.original.specialty}</span>
            <span className='text-paragraph-xs text-text-sub-600'>{row.original.doctor}</span>
          </div>
        ),
      },
      {
        id: 'location',
        accessorKey: 'location',
        header: 'Sede',
        cell: ({ row }) => (
          <div className='flex flex-col gap-0.5'>
            <span className='whitespace-nowrap text-label-sm text-text-strong-950'>{row.original.location}</span>
            <span className='text-paragraph-xs text-text-sub-600'>{row.original.room ?? 'Consultorio por asignar'}</span>
          </div>
        ),
      },
      {
        id: 'status',
        accessorKey: 'status',
        header: 'Estado',
        cell: ({ row }) => (
          <div className='flex flex-col items-start gap-1'>
            <span className='whitespace-nowrap'>
              <AppointmentStatusBadge status={row.original.status} />
            </span>
            {/* La cobertura va como subtítulo del estado para no sumar una columna más. */}
            {row.original.coverage && <span className='text-paragraph-xs text-text-sub-600'>{row.original.coverage}</span>}
            {row.original.rescheduleCount > 0 && (
              <span className='text-paragraph-xs text-text-sub-600'>
                Reprogramada {row.original.rescheduleCount} {row.original.rescheduleCount === 1 ? 'vez' : 'veces'}
              </span>
            )}
          </div>
        ),
      },
      {
        id: 'actions',
        header: () => <span className='sr-only'>Acciones</span>,
        cell: ({ row }) =>
          isAppointmentActionable(row.original) ? (
            <div className='flex justify-end'>
              {/* modal={false}: el menú no bloquea la página, así el modal que abre cada opción recibe el foco sin conflictos. */}
              <Dropdown.Root modal={false}>
                <Dropdown.Trigger asChild>
                  <Button.Root variant='neutral' mode='ghost' size='xsmall' aria-label={`Acciones de la cita ${row.original.code}`}>
                    <Button.Icon as={RiMore2Line} />
                  </Button.Root>
                </Dropdown.Trigger>
                <Dropdown.Content align='end' className='w-[220px]'>
                  <Dropdown.Item onSelect={() => onReschedule(row.original)} aria-label={`Reprogramar la cita ${row.original.code}`}>
                    <Dropdown.ItemIcon as={RiCalendarScheduleLine} />
                    Reprogramar
                  </Dropdown.Item>
                  <Dropdown.Item
                    onSelect={() => onCancel(row.original)}
                    aria-label={`Anular la cita ${row.original.code}`}
                    className='text-error-base'
                  >
                    <Dropdown.ItemIcon as={RiCloseCircleLine} className='text-error-base' />
                    Anular
                  </Dropdown.Item>
                </Dropdown.Content>
              </Dropdown.Root>
            </div>
          ) : null,
      },
    ],
    [onReschedule, onCancel],
  );

  return (
    <DataTable
      columns={columns}
      data={appointments}
      isLoading={isLoading}
      errorMessage={errorMessage}
      onRetry={onRetry}
      emptyIcon={RiCalendarLine}
      emptyTitle={emptyTitle}
      emptyDescription={emptyDescription}
      emptyAction={emptyAction}
      pageSize={10}
      skeletonRows={3}
      getRowId={(row) => String(row.id)}
    />
  );
}
