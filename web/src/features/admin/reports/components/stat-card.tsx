import type { RemixiconComponentType } from '@remixicon/react';

import { cn } from '@/lib/cn';
import { formatInteger } from '@/lib/formatters';

export type StatTone = 'neutral' | 'success' | 'error' | 'warning' | 'faded' | 'primary';

const TONE_STYLES: Record<StatTone, { box: string; icon: string }> = {
  neutral: { box: 'bg-bg-weak-50', icon: 'text-text-sub-600' },
  success: { box: 'bg-success-lighter', icon: 'text-success-base' },
  error: { box: 'bg-error-lighter', icon: 'text-error-base' },
  warning: { box: 'bg-warning-lighter', icon: 'text-warning-base' },
  faded: { box: 'bg-faded-lighter', icon: 'text-faded-base' },
  primary: { box: 'bg-primary-alpha-10', icon: 'text-primary-base' },
};

interface StatCardProps {
  label: string;
  value: number | string;
  /** Texto pequeño bajo el valor (p. ej. "32 % del total"). */
  caption?: string;
  icon: RemixiconComponentType;
  tone?: StatTone;
  /** Resalta la tarjeta con un fondo suave (para el total). */
  highlighted?: boolean;
  className?: string;
}

/** Tarjeta de cifra para los reportes (icono en caja + etiqueta + valor). */
export function StatCard({ label, value, caption, icon: Icon, tone = 'neutral', highlighted = false, className }: StatCardProps) {
  const styles = TONE_STYLES[tone];
  return (
    <div
      className={cn(
        'flex flex-col gap-3 rounded-20 p-4 shadow-regular-xs ring-1 ring-inset ring-stroke-soft-200',
        highlighted ? 'bg-bg-weak-50' : 'bg-bg-white-0',
        className,
      )}
    >
      <div className='flex items-center gap-2.5'>
        <div className={cn('flex size-8 shrink-0 items-center justify-center rounded-full', styles.box)}>
          <Icon className={cn('size-4', styles.icon)} />
        </div>
        <span className='truncate text-label-sm text-text-sub-600'>{label}</span>
      </div>
      <div className='flex min-w-0 flex-col'>
        <span className='text-title-h5 tabular-nums text-text-strong-950'>{typeof value === 'number' ? formatInteger(value) : value}</span>
        {caption && <span className='truncate text-paragraph-xs text-text-soft-400'>{caption}</span>}
      </div>
    </div>
  );
}
