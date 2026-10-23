import * as React from 'react';
import { RiCalendarLine, RiErrorWarningLine, RiLockLine, RiLockUnlockLine } from '@remixicon/react';

import { SlotStatusBadge } from '@/components/appointment-status-badge';
import { EmptyState } from '@/components/empty-state';
import { Skeleton } from '@/components/loading-state';
import * as Button from '@/components/ui/button';
import * as Table from '@/components/ui/table';
import type { SlotDetail } from '@/lib/api/types';
import { formatLongDate, formatTime } from '@/lib/formatters';

interface SlotsTableProps {
  slots: SlotDetail[] | undefined;
  isLoading: boolean;
  errorMessage?: string | null;
  onRetry?: () => void;
  /** Mensaje cuando aún no se ha buscado nada. */
  idle?: boolean;
  onBlock: (slot: SlotDetail) => void;
  onUnblock: (slot: SlotDetail) => void;
  /** Cupo cuya acción está en curso (deshabilita sus botones). */
  pendingSlotId?: number | null;
}

const COLUMNS = 5;

/** "lunes, 26 de octubre de 2026" → "Lunes, 26 de octubre de 2026" */
function capitalizeFirst(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** Tabla de cupos agrupada por fecha (Table AlignUI con una fila de cabecera por día, estilo data-table-demo). */
export function SlotsTable({ slots, isLoading, errorMessage, onRetry, idle = false, onBlock, onUnblock, pendingSlotId }: SlotsTableProps) {
  const groups = React.useMemo(() => {
    const byDate = new Map<string, SlotDetail[]>();
    (slots ?? []).forEach((slot) => {
      const list = byDate.get(slot.date) ?? [];
      list.push(slot);
      byDate.set(slot.date, list);
    });
    return [...byDate.entries()];
  }, [slots]);

  const renderBody = () => {
    if (isLoading) {
      return Array.from({ length: 6 }).map((_, index) => (
        <React.Fragment key={`skeleton-${index}`}>
          <Table.Row>
            {Array.from({ length: COLUMNS }).map((__, cellIndex) => (
              <Table.Cell key={cellIndex}>
                <Skeleton className={cellIndex === 0 ? 'h-4 w-3/4' : 'h-4 w-1/2'} />
              </Table.Cell>
            ))}
          </Table.Row>
          {index < 5 && <Table.RowDivider />}
        </React.Fragment>
      ));
    }
    if (errorMessage) {
      return (
        <Table.Row>
          <Table.Cell colSpan={COLUMNS}>
            <EmptyState
              size='small'
              icon={RiErrorWarningLine}
              title='No pudimos cargar la agenda'
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
      );
    }
    if (idle) {
      return (
        <Table.Row>
          <Table.Cell colSpan={COLUMNS}>
            <EmptyState size='small' icon={RiCalendarLine} title='Elige un médico y un rango de fechas' description='Luego pulsa "Ver cupos" para revisar su agenda.' />
          </Table.Cell>
        </Table.Row>
      );
    }
    if (groups.length === 0) {
      return (
        <Table.Row>
          <Table.Cell colSpan={COLUMNS}>
            <EmptyState size='small' icon={RiCalendarLine} title='Sin cupos en este rango' description='Genera cupos para el médico con el panel "Generar cupos".' />
          </Table.Cell>
        </Table.Row>
      );
    }
    return groups.map(([date, list], groupIndex) => {
      const free = list.filter((s) => s.status === 'FREE').length;
      return (
        <React.Fragment key={date}>
          {groupIndex > 0 && <Table.RowDivider />}
          <Table.Row className='bg-bg-weak-50'>
            <Table.Cell colSpan={COLUMNS} className='py-2'>
              <div className='flex items-center justify-between'>
                <span className='text-label-sm text-text-strong-950'>{capitalizeFirst(formatLongDate(date))}</span>
                <span className='text-paragraph-xs text-text-sub-600'>
                  {list.length} {list.length === 1 ? 'cupo' : 'cupos'} · {free} {free === 1 ? 'libre' : 'libres'}
                </span>
              </div>
            </Table.Cell>
          </Table.Row>
          {list.map((slot, index) => {
            const busy = pendingSlotId === slot.id;
            return (
              <React.Fragment key={slot.id}>
                {index > 0 && <Table.RowDivider />}
                <Table.Row>
                  <Table.Cell className='whitespace-nowrap'>
                    <span className='text-label-sm text-text-strong-950'>
                      {formatTime(slot.startTime)} – {formatTime(slot.endTime)}
                    </span>
                  </Table.Cell>
                  <Table.Cell>
                    <span className='text-paragraph-sm text-text-sub-600'>{slot.room ?? '—'}</span>
                  </Table.Cell>
                  <Table.Cell className='whitespace-nowrap'>
                    <span className='text-paragraph-sm text-text-sub-600'>{slot.location.name}</span>
                  </Table.Cell>
                  <Table.Cell>
                    <SlotStatusBadge status={slot.status} />
                  </Table.Cell>
                  <Table.Cell>
                    <div className='flex justify-end'>
                      {slot.status === 'FREE' && (
                        <Button.Root variant='neutral' mode='ghost' size='xsmall' disabled={busy} onClick={() => onBlock(slot)}>
                          <Button.Icon as={RiLockLine} />
                          Bloquear
                        </Button.Root>
                      )}
                      {slot.status === 'BLOCKED' && (
                        <Button.Root variant='neutral' mode='ghost' size='xsmall' disabled={busy} onClick={() => onUnblock(slot)}>
                          <Button.Icon as={RiLockUnlockLine} />
                          Desbloquear
                        </Button.Root>
                      )}
                    </div>
                  </Table.Cell>
                </Table.Row>
              </React.Fragment>
            );
          })}
        </React.Fragment>
      );
    });
  };

  return (
    <Table.Root>
      <Table.Header>
        <Table.Row>
          <Table.Head>Horario</Table.Head>
          <Table.Head>Consultorio</Table.Head>
          <Table.Head>Sede</Table.Head>
          <Table.Head>Estado</Table.Head>
          <Table.Head className='w-[160px]' />
        </Table.Row>
      </Table.Header>
      <Table.Body>{renderBody()}</Table.Body>
    </Table.Root>
  );
}
