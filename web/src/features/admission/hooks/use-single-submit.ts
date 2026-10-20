import * as React from 'react';

/**
 * Evita envíos duplicados por doble clic. TanStack Query notifica `isPending` de forma asíncrona, así que un
 * segundo clic inmediato todavía encuentra el botón habilitado; este candado se activa en el mismo clic.
 *
 *   const submit = useSingleSubmit();
 *   onClick={() => submit(() => mutation.mutateAsync(variables))}
 *
 * Los errores ya los maneja el `onError` de la mutación, por eso aquí solo se descartan.
 */
export function useSingleSubmit() {
  const pendingRef = React.useRef(false);
  return React.useCallback((run: () => Promise<unknown>) => {
    if (pendingRef.current) return;
    pendingRef.current = true;
    run()
      .catch(() => undefined)
      .finally(() => {
        pendingRef.current = false;
      });
  }, []);
}
