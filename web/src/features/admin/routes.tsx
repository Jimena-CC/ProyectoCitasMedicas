import { Navigate, type RouteObject } from 'react-router';
import {
  RiGroupLine,
  RiHeartPulseLine,
  RiStethoscopeLine,
  RiTimeLine,
} from '@remixicon/react';

import { UnderConstructionPage } from '@/app/pages/under-construction-page';
import { AuditPage } from '@/features/admin/audit/audit-page';
import { ReportsPage } from '@/features/admin/reports/reports-page';
import { ADMIN_ROLES } from '@/features/auth/roles';
import { RequireRole } from '@/features/auth/require-role';

/**
 * Rutas del módulo de Administración, rol ADMIN.
 * La siguiente fase reemplaza cada placeholder por la vista real (CU-021 a CU-027).
 *
 *   /administracion                  → redirige a /administracion/usuarios
 *   /administracion/usuarios         usuarios del backoffice (CU-021)
 *   /administracion/medicos          médicos (CU-022)
 *   /administracion/especialidades   especialidades y sedes (CU-023)
 *   /administracion/horarios         generación y bloqueo de cupos (CU-024)
 *   /administracion/auditoria        registro de auditoría (CU-025)
 *   /administracion/reportes         reportes y exportación CSV (CU-026, CU-027)
 */
export const adminRoutes: RouteObject[] = [
  {
    path: 'administracion',
    element: <RequireRole roles={ADMIN_ROLES} />,
    children: [
      { index: true, element: <Navigate to='/administracion/usuarios' replace /> },
      {
        path: 'usuarios',
        element: <UnderConstructionPage title='Usuarios' description='Crea, edita y desactiva los usuarios del backoffice.' icon={RiGroupLine} />,
      },
      {
        path: 'medicos',
        element: <UnderConstructionPage title='Médicos' description='Registra y actualiza los médicos de la clínica.' icon={RiStethoscopeLine} />,
      },
      {
        path: 'especialidades',
        element: (
          <UnderConstructionPage
            title='Especialidades'
            description='Administra las especialidades, su duración de cita y las sedes donde se atienden.'
            icon={RiHeartPulseLine}
          />
        ),
      },
      {
        path: 'horarios',
        element: <UnderConstructionPage title='Horarios' description='Genera y bloquea los cupos de atención de cada médico.' icon={RiTimeLine} />,
      },
      {
        path: 'auditoria',
        element: <AuditPage />,
      },
      {
        path: 'reportes',
        element: <ReportsPage />,
      },
    ],
  },
];
