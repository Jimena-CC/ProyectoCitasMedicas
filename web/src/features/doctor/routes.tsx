import type { RouteObject } from 'react-router';
import { RiCalendarScheduleLine } from '@remixicon/react';

import { UnderConstructionPage } from '@/app/pages/under-construction-page';
import { DOCTOR_ROLES } from '@/features/auth/roles';
import { RequireRole } from '@/features/auth/require-role';

/**
 * Rutas del módulo del Médico, rol DOCTOR.
 * La siguiente fase reemplaza el placeholder por la agenda del día con registro de asistencia (CU-014, CU-015).
 *
 *   /agenda          agenda del día (acepta ?fecha=yyyy-MM-dd)
 */
export const doctorRoutes: RouteObject[] = [
  {
    path: 'agenda',
    element: <RequireRole roles={DOCTOR_ROLES} />,
    children: [
      {
        index: true,
        element: (
          <UnderConstructionPage
            title='Agenda del día'
            description='Tus citas de hoy ordenadas por hora, con el registro de asistencia de cada paciente.'
            icon={RiCalendarScheduleLine}
          />
        ),
      },
    ],
  },
];
