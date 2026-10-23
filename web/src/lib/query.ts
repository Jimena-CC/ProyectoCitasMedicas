import { QueryClient } from '@tanstack/react-query';

import { ApiError } from '@/lib/api/client';

/**
 * Cliente de TanStack Query compartido por toda la aplicación.
 * Reintenta una sola vez y nunca ante errores del cliente (4xx):
 * una validación o un 404 no van a cambiar por volver a pedirlos.
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error) => {
        if (error instanceof ApiError && error.status > 0 && error.status < 500) return false;
        return failureCount < 1;
      },
      staleTime: 30_000,
      refetchOnWindowFocus: false,
    },
    mutations: {
      retry: 0,
    },
  },
});
