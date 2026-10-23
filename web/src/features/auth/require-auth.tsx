import { Navigate, Outlet, useLocation } from 'react-router';

import { useAuth } from '@/features/auth/use-auth';

/**
 * Protege un grupo de rutas: sin sesión redirige a /login recordando a dónde quería ir el usuario.
 * El aviso de "sesión expirada" lo toma LoginPage de sessionStore.consumeLogoutReason().
 */
export function RequireAuth() {
  const { isAuthenticated } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    const from = `${location.pathname}${location.search}`;
    return <Navigate to='/login' replace state={{ from }} />;
  }
  return <Outlet />;
}
