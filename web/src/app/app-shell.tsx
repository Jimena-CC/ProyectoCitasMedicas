import { Outlet } from 'react-router';

import { Sidebar } from '@/app/sidebar';
import { IdleLogout } from '@/features/auth/idle-logout';

/**
 * Estructura base del backoffice: sidebar fija a la izquierda y contenido con scroll propio.
 * Pensada para 1280 × 720 como mínimo (RNF-18). Las páginas empiezan con <PageHeader />.
 */
export function AppShell() {
  return (
    <div className='flex h-screen min-w-[1280px] overflow-hidden bg-bg-weak-50'>
      <Sidebar />
      <main className='flex min-w-0 flex-1 flex-col overflow-y-auto'>
        <div className='flex w-full flex-1 flex-col gap-6 p-8'>
          <Outlet />
        </div>
      </main>
      <IdleLogout />
    </div>
  );
}
