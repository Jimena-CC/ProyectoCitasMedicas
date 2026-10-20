import * as React from 'react';

import { cn } from '@/lib/cn';

interface PageHeaderProps {
  title: string;
  description?: React.ReactNode;
  /** Botones a la derecha (p. ej. "Nueva cita"). */
  actions?: React.ReactNode;
  /** Texto pequeño sobre el título (p. ej. el módulo o una fecha). */
  eyebrow?: React.ReactNode;
  className?: string;
}

/** Cabecera estándar de página: título text-title-h5, descripción y acciones. También fija el título de la pestaña. */
export function PageHeader({ title, description, actions, eyebrow, className }: PageHeaderProps) {
  React.useEffect(() => {
    document.title = `${title} · MediCitas Anglo`;
  }, [title]);

  return (
    <header className={cn('flex flex-wrap items-start justify-between gap-4', className)}>
      <div className='flex min-w-0 flex-col gap-1'>
        {eyebrow && <div className='text-subheading-xs uppercase text-text-soft-400'>{eyebrow}</div>}
        <h1 className='text-title-h5 text-text-strong-950'>{title}</h1>
        {description && <p className='text-paragraph-sm text-text-sub-600'>{description}</p>}
      </div>
      {actions && <div className='flex shrink-0 items-center gap-2'>{actions}</div>}
    </header>
  );
}
