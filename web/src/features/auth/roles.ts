import type { UserRole } from '@/lib/api/types';

/** Pantalla inicial de cada rol tras iniciar sesión (RNF-17). */
export const ROLE_HOME: Record<UserRole, string> = {
  FRONT_DESK: '/ventanilla',
  DOCTOR: '/agenda',
  ADMIN: '/administracion/usuarios',
};

export function roleHome(role: UserRole): string {
  return ROLE_HOME[role] ?? '/';
}

/** Roles que pueden usar el módulo de ventanilla (/front-desk/** en la API). */
export const FRONT_DESK_ROLES: UserRole[] = ['FRONT_DESK', 'ADMIN'];
export const DOCTOR_ROLES: UserRole[] = ['DOCTOR'];
export const ADMIN_ROLES: UserRole[] = ['ADMIN'];
