import {
  RiCalendarCheckLine,
  RiCheckboxCircleLine,
  RiCloseCircleLine,
  RiTimeLine,
  RiUserUnfollowLine,
  type RemixiconComponentType,
} from '@remixicon/react';

import { Card } from '@/components/card';
import { Skeleton } from '@/components/loading-state';
import * as Badge from '@/components/ui/badge';
import type { AgendaSummary as AgendaSummaryData } from '@/lib/api/types';
import { cn } from '@/lib/cn';
import { formatInteger, formatRate } from '@/lib/formatters';

type BadgeColor = 'orange' | 'green' | 'gray' | 'red';

interface SummaryCardSpec {
  key: keyof AgendaSummaryData;
  label: string;
  icon: RemixiconComponentType;
  box: string;
  iconColor: string;
  /** Color del Badge con la proporción sobre el total (solo tarjetas de estado). */
  badge?: BadgeColor;
}

const CARDS: SummaryCardSpec[] = [
  { key: 'total', label: 'Total de citas', icon: RiCalendarCheckLine, box: 'bg-primary-alpha-10', iconColor: 'text-primary-base' },
  { key: 'booked', label: 'Reservadas', icon: RiTimeLine, box: 'bg-warning-lighter', iconColor: 'text-warning-base', badge: 'orange' },
  { key: 'attended', label: 'Atendidas', icon: RiCheckboxCircleLine, box: 'bg-success-lighter', iconColor: 'text-success-base', badge: 'green' },
  { key: 'noShow', label: 'No asistieron', icon: RiUserUnfollowLine, box: 'bg-faded-lighter', iconColor: 'text-faded-base', badge: 'gray' },
  { key: 'cancelled', label: 'Anuladas', icon: RiCloseCircleLine, box: 'bg-error-lighter', iconColor: 'text-error-base', badge: 'red' },
];

interface AgendaSummaryProps {
  summary: AgendaSummaryData | undefined;
  isLoading: boolean;
}

/** Parte del total del día en porcentaje entero ("40%"), con el mismo formato que los reportes. */
function share(value: number, total: number): string {
  return formatRate(total === 0 ? 0 : value / total, 0);
}

/** Fila de tarjetas con los totales del día (estilo widget de dashboard AlignUI: icono en círculo, etiqueta y cifra). */
export function AgendaSummary({ summary, isLoading }: AgendaSummaryProps) {
  return (
    <div className='grid grid-cols-5 gap-4'>
      {CARDS.map((card) => {
        const value = summary?.[card.key];
        return (
          <Card key={card.key} className='flex flex-col gap-3'>
            <div className='flex items-center justify-between gap-2'>
              <span className='text-paragraph-sm text-text-sub-600'>{card.label}</span>
              <div className={cn('flex size-9 shrink-0 items-center justify-center rounded-full', card.box)}>
                <card.icon className={cn('size-5', card.iconColor)} />
              </div>
            </div>
            {isLoading ? (
              <Skeleton className='h-7 w-16' />
            ) : (
              <div className='flex items-center gap-2'>
                <span className='text-title-h5 text-text-strong-950'>{value === undefined ? '—' : formatInteger(value)}</span>
                {card.badge && summary && (
                  <Badge.Root variant='light' color={card.badge} size='medium'>
                    {share(summary[card.key], summary.total)}
                  </Badge.Root>
                )}
              </div>
            )}
          </Card>
        );
      })}
    </div>
  );
}
