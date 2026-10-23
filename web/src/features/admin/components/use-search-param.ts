import * as React from 'react';
import { useSearchParams } from 'react-router';

/**
 * Estado de texto guardado en un parámetro de la URL (con replace, sin ensuciar el historial),
 * para que filtros y búsquedas sobrevivan a una recarga. El valor por defecto no se escribe en la URL.
 */
export function useSearchParamState(key: string, defaultValue = ''): [string, (value: string) => void] {
  const [searchParams, setSearchParams] = useSearchParams();
  const value = searchParams.get(key) ?? defaultValue;

  const setValue = React.useCallback(
    (next: string) => {
      setSearchParams(
        (prev) => {
          const params = new URLSearchParams(prev);
          if (next === '' || next === defaultValue) params.delete(key);
          else params.set(key, next);
          return params;
        },
        { replace: true },
      );
    },
    [key, defaultValue, setSearchParams],
  );

  return [value, setValue];
}
