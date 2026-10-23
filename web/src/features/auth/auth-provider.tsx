import * as React from 'react';

import { AuthContext, type AuthContextValue } from '@/features/auth/auth-context';
import { authApi } from '@/lib/api/auth';
import { sessionStore, type Session } from '@/lib/session';

/**
 * Expone la sesión a toda la app. El estado real vive en sessionStore (src/lib/session.ts),
 * así el cliente de la API puede cerrarla ante un 401 sin depender de React.
 */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const session = React.useSyncExternalStore<Session | null>(sessionStore.subscribe, sessionStore.get);

  // Cierre automático cuando vence el token (RNF-10: máximo 8 h).
  React.useEffect(() => {
    if (!session) return;
    const remaining = new Date(session.expiresAt).getTime() - Date.now();
    if (remaining <= 0) {
      sessionStore.clear('expired');
      return;
    }
    const timer = window.setTimeout(() => sessionStore.clear('expired'), Math.min(remaining, 0x7fffffff));
    return () => window.clearTimeout(timer);
  }, [session]);

  const value = React.useMemo<AuthContextValue>(
    () => ({
      user: session?.user ?? null,
      token: session?.token ?? null,
      expiresAt: session?.expiresAt ?? null,
      isAuthenticated: session !== null,
      login: async (credentials) => {
        const response = await authApi.login(credentials);
        sessionStore.set({ token: response.token, expiresAt: response.expiresAt, user: response.user });
        return response.user;
      },
      logout: (reason = 'manual') => sessionStore.clear(reason),
    }),
    [session],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
