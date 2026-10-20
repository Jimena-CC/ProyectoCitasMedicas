import * as React from 'react';
import { useQuery } from '@tanstack/react-query';
import { addDays, format, parse } from 'date-fns';
import { es } from 'date-fns/locale';
import { RiArrowLeftSLine, RiArrowRightSLine, RiCalendarCloseLine, RiErrorWarningLine, RiRefreshLine, RiStethoscopeLine } from '@remixicon/react';

import { EmptyState } from '@/components/empty-state';
import { LoadingState } from '@/components/loading-state';
import * as Button from '@/components/ui/button';
import { catalogApi, getErrorMessage, type AvailabilityDay, type AvailabilitySlot, type IsoDate } from '@/lib/api';
import { cn } from '@/lib/cn';
import { formatLongDate, formatTime, toIsoDate, todayIso } from '@/lib/formatters';

import { admissionKeys } from '../lib/patients';

/** Días que se muestran por vez (la API admite hasta 14). */
const WINDOW_DAYS = 7;

export interface SelectedSlot {
  date: IsoDate;
  slot: AvailabilitySlot;
}

interface AvailabilityPickerProps {
  specialtyId: number;
  locationId: number;
  selectedSlotId: number | null;
  onSelect: (selection: SelectedSlot) => void;
  disabled?: boolean;
  /** Día con el que arranca la grilla (p. ej. el del cupo que se acaba de perder); por defecto, hoy. */
  initialDate?: IsoDate | null;
}

function toDate(iso: IsoDate): Date {
  return parse(iso, 'yyyy-MM-dd', new Date());
}

/**
 * Selector de cupo libre (RF-07): tira de días de la semana con el total de cupos y, para el día elegido,
 * la grilla de horarios agrupada por médico. El cupo elegido se resalta con el anillo primario.
 * Lo comparten el asistente de agendar (paso 3) y el modal de reprogramar.
 */
export function AvailabilityPicker({ specialtyId, locationId, selectedSlotId, onSelect, disabled = false, initialDate = null }: AvailabilityPickerProps) {
  const today = todayIso();
  const startDate = initialDate && initialDate > today ? initialDate : null;
  const [windowStart, setWindowStart] = React.useState<IsoDate>(startDate ?? today);
  const [selectedDate, setSelectedDate] = React.useState<IsoDate | null>(startDate);
  const windowEnd = toIsoDate(addDays(toDate(windowStart), WINDOW_DAYS - 1));

  const query = useQuery({
    queryKey: admissionKeys.availability(specialtyId, locationId, windowStart, windowEnd),
    queryFn: () => catalogApi.getAvailability({ specialtyId, locationId, from: windowStart, to: windowEnd }),
    staleTime: 15_000,
  });

  const days = React.useMemo<AvailabilityDay[]>(() => {
    const byDate = new Map((query.data?.days ?? []).map((day) => [day.date, day]));
    return Array.from({ length: WINDOW_DAYS }, (_, index) => {
      const date = toIsoDate(addDays(toDate(windowStart), index));
      return byDate.get(date) ?? { date, freeSlots: 0, slots: [] };
    });
  }, [query.data, windowStart]);

  const moveWindow = (direction: 1 | -1) => {
    const next = toIsoDate(addDays(toDate(windowStart), direction * WINDOW_DAYS));
    setWindowStart(next < today ? today : next);
    setSelectedDate(null);
  };

  // Día mostrado: el elegido si tiene cupos; si no, el primer día de la semana con cupos.
  const chosenDay = selectedDate ? days.find((day) => day.date === selectedDate) : undefined;
  const selectedDay = query.data ? (chosenDay && chosenDay.freeSlots > 0 ? chosenDay : (days.find((day) => day.freeSlots > 0) ?? null)) : null;
  const doctorGroups = React.useMemo(() => {
    const groups = new Map<number, { doctor: AvailabilitySlot['doctor']; room: string | null; slots: AvailabilitySlot[] }>();
    for (const slot of selectedDay?.slots ?? []) {
      const group = groups.get(slot.doctor.id);
      if (group) group.slots.push(slot);
      else groups.set(slot.doctor.id, { doctor: slot.doctor, room: slot.room, slots: [slot] });
    }
    return [...groups.values()];
  }, [selectedDay]);

  const weekHasSlots = days.some((day) => day.freeSlots > 0);

  return (
    <div className='flex flex-col gap-4'>
      <div className='flex items-center justify-between gap-3'>
        <div className='flex flex-col gap-0.5'>
          <span className='text-label-md text-text-strong-950'>
            {format(toDate(windowStart), "d 'de' MMMM", { locale: es })} – {format(toDate(windowEnd), "d 'de' MMMM 'de' yyyy", { locale: es })}
          </span>
          <span className='text-paragraph-xs text-text-sub-600'>Elige un día y luego el horario con el médico que prefieras.</span>
        </div>
        <div className='flex items-center gap-2'>
          <Button.Root variant='neutral' mode='ghost' size='xsmall' onClick={() => void query.refetch()} disabled={query.isFetching || disabled}>
            <Button.Icon as={RiRefreshLine} className={cn(query.isFetching && 'animate-spin')} />
            Actualizar
          </Button.Root>
          <Button.Root variant='neutral' mode='stroke' size='xsmall' onClick={() => moveWindow(-1)} disabled={windowStart <= today || disabled}>
            <Button.Icon as={RiArrowLeftSLine} />
            Semana anterior
          </Button.Root>
          <Button.Root variant='neutral' mode='stroke' size='xsmall' onClick={() => moveWindow(1)} disabled={disabled}>
            Semana siguiente
            <Button.Icon as={RiArrowRightSLine} />
          </Button.Root>
        </div>
      </div>

      <div className='grid grid-cols-7 gap-2' role='radiogroup' aria-label='Día de la cita'>
        {days.map((day) => {
          const isSelected = day.date === selectedDay?.date;
          const isEmpty = day.freeSlots === 0;
          return (
            <button
              key={day.date}
              type='button'
              onClick={() => setSelectedDate(day.date)}
              disabled={isEmpty || disabled || query.isPending}
              role='radio'
              aria-checked={isSelected}
              aria-label={`${formatLongDate(day.date)}, ${isEmpty ? 'sin cupos' : `${day.freeSlots} ${day.freeSlots === 1 ? 'cupo libre' : 'cupos libres'}`}`}
              className={cn(
                'flex flex-col items-center gap-0.5 rounded-10 px-2 py-2.5 text-center transition duration-200 ease-out',
                'focus:outline-none focus-visible:shadow-button-primary-focus',
                'ring-1 ring-inset ring-stroke-soft-200 bg-bg-white-0 text-text-strong-950 hover:bg-bg-weak-50',
                'disabled:cursor-not-allowed disabled:bg-bg-weak-50 disabled:text-text-disabled-300 disabled:hover:bg-bg-weak-50',
                isSelected && 'bg-primary-base text-static-white ring-primary-base hover:bg-primary-darker',
              )}
            >
              <span className={cn('text-subheading-2xs uppercase', isSelected ? 'text-static-white/80' : 'text-text-soft-400')}>
                {format(toDate(day.date), 'EEE', { locale: es })}
              </span>
              <span className='text-label-md'>{format(toDate(day.date), 'd')}</span>
              <span className={cn('text-paragraph-xs', isSelected ? 'text-static-white/80' : isEmpty ? 'text-text-disabled-300' : 'text-text-sub-600')}>
                {query.isPending ? '…' : isEmpty ? 'Sin cupos' : `${day.freeSlots} ${day.freeSlots === 1 ? 'cupo' : 'cupos'}`}
              </span>
            </button>
          );
        })}
      </div>

      {query.isPending ? (
        <LoadingState rows={3} label='Buscando horarios disponibles…' />
      ) : query.isError ? (
        <EmptyState
          size='small'
          icon={RiErrorWarningLine}
          title='No pudimos cargar los horarios'
          description={getErrorMessage(query.error)}
          action={
            <Button.Root variant='neutral' mode='stroke' size='xsmall' onClick={() => void query.refetch()}>
              Reintentar
            </Button.Root>
          }
        />
      ) : !weekHasSlots ? (
        <EmptyState
          size='small'
          icon={RiCalendarCloseLine}
          title='No hay cupos libres en estos días'
          description='Prueba con la semana siguiente o con otra sede.'
          action={
            <Button.Root variant='neutral' mode='stroke' size='xsmall' onClick={() => moveWindow(1)} disabled={disabled}>
              Ver la semana siguiente
              <Button.Icon as={RiArrowRightSLine} />
            </Button.Root>
          }
        />
      ) : selectedDay ? (
        <div className='flex flex-col gap-4 rounded-10 bg-bg-weak-50 p-4'>
          <div className='text-label-sm text-text-strong-950'>{formatLongDate(selectedDay.date)}</div>
          {doctorGroups.map((group) => (
            <div key={group.doctor.id} className='flex flex-col gap-2.5'>
              <div className='flex items-center gap-2.5'>
                <div className='flex size-8 items-center justify-center rounded-full bg-bg-white-0 ring-1 ring-inset ring-stroke-soft-200'>
                  <RiStethoscopeLine className='size-4 text-text-sub-600' />
                </div>
                <div className='flex flex-col'>
                  <span className='text-label-sm text-text-strong-950'>{group.doctor.fullName}</span>
                  <span className='text-paragraph-xs text-text-sub-600'>
                    CMP {group.doctor.license}
                    {group.room && ` · ${group.room}`}
                  </span>
                </div>
              </div>
              <div className='grid grid-cols-6 gap-2' role='radiogroup' aria-label={`Horario con ${group.doctor.fullName}`}>
                {group.slots.map((slot) => {
                  const isSelected = slot.slotId === selectedSlotId;
                  return (
                    <button
                      key={slot.slotId}
                      type='button'
                      disabled={disabled}
                      role='radio'
                      aria-checked={isSelected}
                      aria-label={`${formatTime(slot.startTime)} a ${formatTime(slot.endTime)} con ${slot.doctor.fullName}${slot.room ? `, ${slot.room}` : ''}`}
                      onClick={() => onSelect({ date: selectedDay.date, slot })}
                      className={cn(
                        'flex flex-col items-center rounded-10 bg-bg-white-0 px-3 py-2 text-center ring-1 ring-inset ring-stroke-soft-200 transition duration-200 ease-out',
                        'focus:outline-none focus-visible:shadow-button-primary-focus',
                        'hover:bg-bg-white-0 hover:ring-stroke-sub-300 disabled:cursor-not-allowed disabled:opacity-60',
                        isSelected && 'bg-primary-alpha-10 ring-2 ring-primary-base hover:ring-primary-base',
                      )}
                    >
                      <span className={cn('text-label-sm', isSelected ? 'text-primary-base' : 'text-text-strong-950')}>{formatTime(slot.startTime)}</span>
                      <span className={cn('text-paragraph-xs', isSelected ? 'text-primary-base' : 'text-text-sub-600')}>hasta {formatTime(slot.endTime)}</span>
                      {slot.room && slot.room !== group.room && <span className='text-paragraph-xs text-text-sub-600'>{slot.room}</span>}
                    </button>
                  );
                })}
              </div>
            </div>
          ))}
        </div>
      ) : null}
    </div>
  );
}
