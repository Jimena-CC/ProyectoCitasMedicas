import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router';

import './styles/globals.css';

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
          <Toaster />
        </Tooltip.Provider>
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
);
