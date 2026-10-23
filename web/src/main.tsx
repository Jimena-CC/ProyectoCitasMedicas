import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router';

import './styles/globals.css';

import { TOAST_POSITION } from '@/components/notify';
import { ToastDismissOnModal } from '@/components/toast-dismiss-on-modal';
import { Toaster } from '@/components/ui/toast';
import * as Tooltip from '@/components/ui/tooltip';
import { AuthProvider } from '@/features/auth/auth-provider';
import { queryClient } from '@/lib/query';
import { router } from '@/app/router';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <Tooltip.Provider delayDuration={200}>
          <RouterProvider router={router} />
          {/*
            Arriba al centro para no tapar los botones de acción, que van abajo en pantallas y
            modales. notify pasa la misma posición a toast.custom (evita una segunda pila vacía).
            Pila siempre desplegada: plegada, sonner encoge los avisos anteriores detrás del último
            y se ven como tarjetas vacías. Al abrir un modal se cierran para no tapar su cabecera.
          */}
          <Toaster position={TOAST_POSITION} expand />
          <ToastDismissOnModal />
        </Tooltip.Provider>
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
);
