import { Navigate, type RouteObject } from 'react-router';
import { RiCalendarCheckLine, RiCalendarLine, RiUserAddLine, RiUserLine, RiUserSearchLine } from '@remixicon/react';

import { UnderConstructionPage } from '@/app/pages/under-construction-page';
import { FRONT_DESK_ROLES } from '@/features/auth/roles';
import { RequireRole } from '@/features/auth/require-role';

/**
 * Rutas del módulo de Ventanilla (admisión), roles FRONT_DESK y ADMIN.
 * La siguiente fase reemplaza cada UnderConstructionPage por la vista real (CU-016 a CU-020).
 *
 *   /ventanilla                      → redirige a /ventanilla/pacientes
 *   /ventanilla/pacientes            buscar paciente por documento (CU-016)
 *   /ventanilla/pacientes/nuevo      registrar paciente (CU-016)
 *   /ventanilla/pacientes/:patientId ficha del paciente: datos, seguro (CU-017) y sus citas
 *   /ventanilla/agendar              agendar cita en ventanilla (CU-018), acepta ?paciente=<id>
 *   /ventanilla/citas                citas: reprogramar (CU-019) y anular (CU-020)
 */
export const admissionRoutes: RouteObject[] = [
  {
    path: 'ventanilla',
    element: <RequireRole roles={FRONT_DESK_ROLES} />,
    children: [
      { index: true, element: <Navigate to='/ventanilla/pacientes' replace /> },
      {
        path: 'pacientes',
        element: (
          <UnderConstructionPage
            title='Buscar paciente'
            description='Busca al paciente por su documento o regístralo si es la primera vez que se atiende.'
            icon={RiUserSearchLine}
          />
        ),
      },
      {
        path: 'pacientes/nuevo',
        element: <UnderConstructionPage title='Registrar paciente' description='Crea el perfil de un paciente nuevo.' icon={RiUserAddLine} />,
      },
      {
        path: 'pacientes/:patientId',
        element: (
          <UnderConstructionPage title='Ficha del paciente' description='Datos del paciente, su seguro y sus citas.' icon={RiUserLine} />
        ),
      },
      {
        path: 'agendar',
        element: (
          <UnderConstructionPage
            title='Agendar cita'
            description='Reserva una cita a nombre del paciente eligiendo especialidad, sede y horario.'
            icon={RiCalendarCheckLine}
          />
        ),
      },
      {
        path: 'citas',
        element: (
          <UnderConstructionPage title='Citas' description='Consulta las citas de un paciente para reprogramarlas o anularlas.' icon={RiCalendarLine} />
        ),
      },
    ],
  },
];
