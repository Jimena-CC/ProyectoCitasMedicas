import * as ProgressBar from '@/components/ui/progress-bar';
import { toPercent, type BarColor } from '@/features/admin/reports/report-range';
import { cn } from '@/lib/cn';
import { formatRate } from '@/lib/formatters';

const TEXT_COLOR: Record<BarColor, string> = {
  green: 'text-success-base',
  orange: 'text-warning-base',
  red: 'text-error-base',
  blue: 'text-information-base',
};

interface RateBarProps {
  /** Tasa de 0 a 1. */
  rate: number;
  color: BarColor;
  /** Tamaño del texto del porcentaje. */
  size?: 'small' | 'large';
  className?: string;
}

/** Tasa con barra de progreso coloreada por umbral (ejemplo progress-bar-with-label). */
export function RateBar({ rate, color, size = 'small', className }: RateBarProps) {
  return (
    <div className={cn('flex items-center gap-3', className)}>
      <ProgressBar.Root value={toPercent(rate)} max={100} color={color} className='flex-1' />
      <span className={cn('shrink-0 tabular-nums', size === 'large' ? 'text-title-h6' : 'w-14 text-right text-label-sm', TEXT_COLOR[color])}>
        {formatRate(rate)}
      </span>
    </div>
  );
}
