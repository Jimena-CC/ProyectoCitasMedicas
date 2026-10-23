import * as React from 'react';

import { AuthContext, type AuthContextValue } from '@/features/auth/auth-context';
import type { UserInfo } from '@/lib/api/types';

/** Sesión actual: user, token, isAuthenticated, login(), logout(). */
export function useAuth(): AuthContextValue {
  const context = React.useContext(AuthContext);
  if (!context) throw new Error('useAuth debe usarse dentro de <AuthProvider>.');
  return context;
}

/**
 * Igual que useAuth pero garantiza que hay usuario (para páginas detrás de RequireAuth).
 * Evita comprobar `user !== null` en cada vista.
 */
export function useCurrentUser(): UserInfo {
  const { user } = useAuth();
  if (!user) throw new Error('useCurrentUser requiere una sesión iniciada (envuelve la ruta en RequireAuth).');
  return user;
}
