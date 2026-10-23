import * as React from 'react';
import { RiArrowLeftSLine, RiArrowRightSLine, RiCalendarLine } from '@remixicon/react';
import { es } from 'date-fns/locale';

import * as ButtonGroup from '@/components/ui/button-group';
import { Calendar } from '@/components/ui/datepicker';
import * as Popover from '@/components/ui/popover';
import type { IsoDate } from '@/lib/api/types';
import { formatShortDate, todayIso, toIsoDate } from '@/lib/formatters';
import { parseIsoDate, shiftIsoDate } from '@/features/doctor/agenda-date';

interface DateNavigatorProps {
  date: IsoDate;
  onChange: (date: IsoDate) => void;
}

/**
 * Navegador de fecha de la agenda: ButtonGroup (button-group-demo) con "Día anterior" / "Hoy" / "Día siguiente"
 * y un cuarto botón que abre el calendario en un Popover (datepicker-popover).
 */
export function DateNavigator({ date, onChange }: DateNavigatorProps) {
  const [open, setOpen] = React.useState(false);
  const today = todayIso();
  const selected = parseIsoDate(date);

  const handleSelect = (next: Date | undefined) => {
    if (!next) return;
    onChange(toIsoDate(next));
    setOpen(false);
  };

  return (
    <ButtonGroup.Root size='xsmall' role='group' aria-label='Cambiar el día de la agenda'>
      <ButtonGroup.Item type='button' onClick={() => onChange(shiftIsoDate(date, -1))}>
        <ButtonGroup.Icon as={RiArrowLeftSLine} />
        Día anterior
      </ButtonGroup.Item>
      <ButtonGroup.Item type='button' onClick={() => onChange(today)} data-state={date === today ? 'on' : undefined}>
        Hoy
      </ButtonGroup.Item>
      <ButtonGroup.Item type='button' onClick={() => onChange(shiftIsoDate(date, 1))}>
        Día siguiente
        <ButtonGroup.Icon as={RiArrowRightSLine} />
      </ButtonGroup.Item>
      <Popover.Root open={open} onOpenChange={setOpen}>
        <Popover.Trigger asChild>
          <ButtonGroup.Item type='button' size='xsmall' aria-label='Elegir una fecha' data-state={open ? 'on' : undefined}>
            <ButtonGroup.Icon as={RiCalendarLine} size='xsmall' />
            {formatShortDate(date)}
          </ButtonGroup.Item>
        </Popover.Trigger>
        <Popover.Content className='p-0' showArrow={false} align='end'>
          <Calendar mode='single' locale={es} selected={selected} defaultMonth={selected} onSelect={handleSelect} />
        </Popover.Content>
      </Popover.Root>
    </ButtonGroup.Root>
  );
}
