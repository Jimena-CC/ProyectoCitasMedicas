import { cn } from '@/lib/cn';

interface SkeletonProps {
  className?: string;
}

/** Bloque gris animado; combinar con clases de tamaño (h-4 w-32, size-10 rounded-full...). */
export function Skeleton({ className }: SkeletonProps) {
  return <div aria-hidden className={cn('animate-pulse rounded-md bg-bg-soft-200', className)} />;
}

interface LoadingStateProps {
  /** Número de líneas a simular. */
  rows?: number;
  /** Texto accesible para lectores de pantalla. */
  label?: string;
  className?: string;
}

/** Esqueleto de carga genérico para tarjetas y listas. */
export function LoadingState({ rows = 4, label = 'Cargando…', className }: LoadingStateProps) {
  return (
    <div role='status' aria-live='polite' aria-label={label} className={cn('flex flex-col gap-3', className)}>
      {Array.from({ length: rows }).map((_, index) => (
        <div key={index} className='flex items-center gap-3'>
          <Skeleton className='size-9 shrink-0 rounded-full' />
          <div className='flex flex-1 flex-col gap-2'>
            <Skeleton className={cn('h-3.5', index % 2 === 0 ? 'w-2/5' : 'w-1/3')} />
            <Skeleton className={cn('h-3', index % 2 === 0 ? 'w-3/5' : 'w-1/2')} />
          </div>
        </div>
      ))}
      <span className='sr-only'>{label}</span>
    </div>
  );
}

/** Esqueleto de una página completa (cabecera + tarjeta), para suspensos de ruta. */
export function LoadingPage() {
  return (
    <div className='flex flex-col gap-6'>
      <div className='flex flex-col gap-2'>
        <Skeleton className='h-7 w-56' />
        <Skeleton className='h-4 w-80' />
      </div>
      <div className='rounded-20 bg-bg-white-0 p-5 shadow-regular-xs ring-1 ring-inset ring-stroke-soft-200'>
        <LoadingState rows={5} />
      </div>
    </div>
  );
}
