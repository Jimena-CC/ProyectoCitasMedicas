import type { RouteObject } from 'react-router';

import { DOCTOR_ROLES } from '@/features/auth/roles';
import { RequireRole } from '@/features/auth/require-role';
import { AgendaPage } from '@/features/doctor/agenda-page';

/**
 * Rutas del módulo del Médico, rol DOCTOR.
 *
 *   /agenda          agenda del día con registro de asistencia (CU-014, CU-015); acepta ?fecha=yyyy-MM-dd
 */
export const doctorRoutes: RouteObject[] = [
  {
    path: 'agenda',
    element: <RequireRole roles={DOCTOR_ROLES} />,
    children: [{ index: true, element: <AgendaPage /> }],
  },
];
