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

/** Primer segmento de cada módulo y los roles que pueden abrirlo (debe coincidir con los RequireRole de cada feature). */
const SECTION_ROLES: Record<string, UserRole[]> = {
  ventanilla: FRONT_DESK_ROLES,
  agenda: DOCTOR_ROLES,
  administracion: ADMIN_ROLES,
};

/**
 * Indica si un rol puede abrir una ruta interna del backoffice.
 * Se usa al volver a la ruta pedida tras el login, para que el siguiente usuario de un
 * equipo compartido no aterrice en la pantalla del anterior.
 */
export function canAccessPath(role: UserRole, path: string): boolean {
  if (!path.startsWith('/') || path.startsWith('//')) return false;
  const section = path.split(/[/?#]/)[1] ?? '';
  const allowed = SECTION_ROLES[section];
  return allowed !== undefined && allowed.includes(role);
}
