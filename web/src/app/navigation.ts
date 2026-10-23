import {
  RiBarChartBoxLine,
  RiCalendarCheckLine,
  RiCalendarLine,
  RiCalendarScheduleLine,
  RiFileList3Line,
  RiGroupLine,
  RiHeartPulseLine,
  RiStethoscopeLine,
  RiTimeLine,
  RiUserSearchLine,
  type RemixiconComponentType,
} from '@remixicon/react';

import { ADMIN_ROLES, DOCTOR_ROLES, FRONT_DESK_ROLES } from '@/features/auth/roles';
import type { UserRole } from '@/lib/api/types';

export interface NavItem {
  label: string;
  to: string;
  icon: RemixiconComponentType;
  /** Marca activo también en rutas hijas (por defecto sí). */
  end?: boolean;
}

export interface NavGroup {
  label: string;
  roles: UserRole[];
  items: NavItem[];
}

/**
 * Menú lateral por rol (RNF-17). El orden importa: el administrador ve primero su módulo y después Ventanilla.
 * Las rutas son en español y kebab-case; cada feature las registra en src/features/<rol>/routes.tsx.
 */
export const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Consulta',
    roles: DOCTOR_ROLES,
    items: [{ label: 'Agenda del día', to: '/agenda', icon: RiCalendarScheduleLine }],
  },
  {
    label: 'Administración',
    roles: ADMIN_ROLES,
    items: [
      { label: 'Usuarios', to: '/administracion/usuarios', icon: RiGroupLine },
      { label: 'Médicos', to: '/administracion/medicos', icon: RiStethoscopeLine },
      { label: 'Especialidades', to: '/administracion/especialidades', icon: RiHeartPulseLine },
      { label: 'Horarios', to: '/administracion/horarios', icon: RiTimeLine },
      { label: 'Auditoría', to: '/administracion/auditoria', icon: RiFileList3Line },
      { label: 'Reportes', to: '/administracion/reportes', icon: RiBarChartBoxLine },
    ],
  },
  {
    label: 'Ventanilla',
    roles: FRONT_DESK_ROLES,
    items: [
      { label: 'Buscar paciente', to: '/ventanilla/pacientes', icon: RiUserSearchLine },
      { label: 'Agendar cita', to: '/ventanilla/agendar', icon: RiCalendarCheckLine },
      { label: 'Citas', to: '/ventanilla/citas', icon: RiCalendarLine },
    ],
  },
];

export function navGroupsFor(role: UserRole): NavGroup[] {
  return NAV_GROUPS.filter((group) => group.roles.includes(role));
}
