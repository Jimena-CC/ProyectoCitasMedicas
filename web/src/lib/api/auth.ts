import { http } from '@/lib/api/client';
import type { LoginRequest, LoginResponse, UserInfo } from '@/lib/api/types';

/** POST /auth/login — 401 INVALID_CREDENTIALS · 403 USER_INACTIVE · 400 INVALID_DATA. */
export function login(credentials: LoginRequest): Promise<LoginResponse> {
  return http.post<LoginResponse>('/auth/login', credentials, { auth: false });
}

/** GET /auth/me — usuario de la sesión actual. */
export function me(): Promise<UserInfo> {
  return http.get<UserInfo>('/auth/me');
}

export const authApi = { login, me };
