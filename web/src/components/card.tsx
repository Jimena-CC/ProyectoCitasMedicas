import * as React from 'react';

import { cn } from '@/lib/cn';

interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  /** Quita el padding interno (útil cuando el contenido es una tabla a sangre). */
  flush?: boolean;
}

/** Tarjeta blanca estándar del backoffice (rounded-20, sombra suave y borde). */
export function Card({ className, flush = false, ...rest }: CardProps) {
  return (
    <section
      className={cn('rounded-20 bg-bg-white-0 shadow-regular-xs ring-1 ring-inset ring-stroke-soft-200', !flush && 'p-5', className)}
      {...rest}
    />
  );
}

interface CardHeaderProps {
  title: string;
  description?: React.ReactNode;
  actions?: React.ReactNode;
  className?: string;
}

/** Cabecera de sección dentro de una tarjeta (título text-label-lg). */
export function CardHeader({ title, description, actions, className }: CardHeaderProps) {
  return (
    <div className={cn('flex flex-wrap items-start justify-between gap-3', className)}>
      <div className='flex flex-col gap-0.5'>
        <h2 className='text-label-lg text-text-strong-950'>{title}</h2>
        {description && <p className='text-paragraph-sm text-text-sub-600'>{description}</p>}
      </div>
      {actions && <div className='flex items-center gap-2'>{actions}</div>}
    </div>
  );
}
