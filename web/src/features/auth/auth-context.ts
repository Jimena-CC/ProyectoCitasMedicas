import * as React from 'react';

import type { LoginRequest, UserInfo } from '@/lib/api/types';
import type { LogoutReason } from '@/lib/session';

export interface AuthContextValue {
  user: UserInfo | null;
  token: string | null;
  expiresAt: string | null;
  isAuthenticated: boolean;
  /** Inicia sesión contra la API y guarda la sesión. Lanza ApiError si falla. */
  login: (credentials: LoginRequest) => Promise<UserInfo>;
  /** Cierra la sesión; el motivo se muestra en /login (salvo 'manual'). */
  logout: (reason?: LogoutReason) => void;
}

export const AuthContext = React.createContext<AuthContextValue | null>(null);
