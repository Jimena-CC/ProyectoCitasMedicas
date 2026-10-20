import type { StatusTotals } from '@/lib/api';
import { formatShareOf } from '@/features/admin/reports/report-format';
import { cn } from '@/lib/cn';
import { formatInteger } from '@/lib/formatters';

interface Segment {
  key: keyof Omit<StatusTotals, 'total'>;
  label: string;
  color: string;
}

const SEGMENTS: Segment[] = [
  { key: 'booked', label: 'Reservadas', color: 'bg-warning-base' },
  { key: 'attended', label: 'Atendidas', color: 'bg-success-base' },
  { key: 'cancelled', label: 'Anuladas', color: 'bg-error-base' },
  { key: 'noShow', label: 'No asistieron', color: 'bg-faded-base' },
];

function percentOf(value: number, total: number): number {
  return total === 0 ? 0 : (value / total) * 100;
}

/** Gráfico de barras horizontales sin librerías: una barra por estado con cifra y porcentaje del total. */
export function StatusBarChart({ totals, className }: { totals: StatusTotals; className?: string }) {
  const max = Math.max(...SEGMENTS.map((segment) => totals[segment.key]), 1);
  return (
    <div className={cn('flex flex-col gap-4', className)}>
      {/* Barra apilada con la distribución completa */}
      <div className='flex h-3 w-full overflow-hidden rounded-full bg-bg-soft-200' role='img' aria-label='Distribución de citas por estado'>
        {SEGMENTS.map((segment) => {
          const width = percentOf(totals[segment.key], totals.total);
          return width > 0 ? <div key={segment.key} className={segment.color} style={{ width: `${width}%` }} title={segment.label} /> : null;
        })}
      </div>
      {/* Una fila por estado */}
      <ul className='flex flex-col gap-3'>
        {SEGMENTS.map((segment) => {
          const value = totals[segment.key];
          return (
            <li key={segment.key} className='grid grid-cols-[120px_1fr_140px] items-center gap-3'>
              <span className='flex items-center gap-2 text-label-sm text-text-strong-950'>
                <span className={cn('size-2.5 shrink-0 rounded-full', segment.color)} aria-hidden />
                {segment.label}
              </span>
              <div className='h-2.5 w-full rounded-full bg-bg-weak-50'>
                <div className={cn('h-full rounded-full transition-all duration-300 ease-out', segment.color)} style={{ width: `${(value / max) * 100}%` }} />
              </div>
              <span className='text-right text-paragraph-sm tabular-nums text-text-sub-600'>
                <span className='text-label-sm text-text-strong-950'>{formatInteger(value)}</span> · {formatShareOf(value, totals.total)}
              </span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
