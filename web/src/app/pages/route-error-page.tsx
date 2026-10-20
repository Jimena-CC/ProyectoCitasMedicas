import { isRouteErrorResponse, useRouteError } from 'react-router';
import { RiErrorWarningLine, RiRefreshLine } from '@remixicon/react';

import { EmptyState } from '@/components/empty-state';
import * as Button from '@/components/ui/button';

/** Pantalla de respaldo cuando una vista lanza un error inesperado (errorElement del router). */
export function RouteErrorPage() {
  const error = useRouteError();
  const notFound = isRouteErrorResponse(error) && error.status === 404;

  return (
    <div className='flex min-h-screen items-center justify-center bg-bg-weak-50 p-8'>
      <div className='w-full max-w-[480px] rounded-20 bg-bg-white-0 p-8 shadow-regular-xs ring-1 ring-inset ring-stroke-soft-200'>
        <EmptyState
          icon={RiErrorWarningLine}
          title={notFound ? 'No encontramos esta página' : 'Algo salió mal'}
          description={
            notFound
              ? 'La dirección que ingresaste no existe.'
              : 'Ocurrió un problema inesperado al mostrar esta pantalla. Vuelve a cargar la página e inténtalo de nuevo.'
          }
          action={
            <Button.Root variant='neutral' mode='stroke' size='small' onClick={() => window.location.assign('/')}>
              <Button.Icon as={RiRefreshLine} />
              Volver al inicio
            </Button.Root>
          }
        />
      </div>
    </div>
  );
}
