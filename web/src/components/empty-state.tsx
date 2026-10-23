import * as React from 'react';
import { RiInboxLine, type RemixiconComponentType } from '@remixicon/react';

import { cn } from '@/lib/cn';

interface EmptyStateProps {
  icon?: RemixiconComponentType;
  title: string;
  description?: React.ReactNode;
  /** Botón o enlace de acción. */
  action?: React.ReactNode;
  size?: 'small' | 'medium';
  className?: string;
}

/** Estado vacío centrado (sin resultados, sin datos, sección en construcción). */
export function EmptyState({ icon: Icon = RiInboxLine, title, description, action, size = 'medium', className }: EmptyStateProps) {
  return (
    <div className={cn('flex flex-col items-center justify-center text-center', size === 'medium' ? 'gap-4 py-12' : 'gap-3 py-8', className)}>
      <div
        className={cn(
          'flex items-center justify-center rounded-full bg-bg-weak-50 ring-1 ring-inset ring-stroke-soft-200',
          size === 'medium' ? 'size-14' : 'size-10',
        )}
      >
        <Icon className={cn('text-text-sub-600', size === 'medium' ? 'size-7' : 'size-5')} />
      </div>
      <div className='flex max-w-[380px] flex-col gap-1'>
        <div className={cn('text-text-strong-950', size === 'medium' ? 'text-label-md' : 'text-label-sm')}>{title}</div>
        {description && <div className='text-paragraph-sm text-text-sub-600'>{description}</div>}
      </div>
      {action && <div className='flex items-center gap-2'>{action}</div>}
    </div>
  );
}
