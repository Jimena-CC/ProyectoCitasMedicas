import { Navigate, type RouteObject } from 'react-router';

import { AuditPage } from '@/features/admin/audit/audit-page';
import { ReportsPage } from '@/features/admin/reports/reports-page';
import { ADMIN_ROLES } from '@/features/auth/roles';
import { RequireRole } from '@/features/auth/require-role';

import { DoctorsPage } from './doctors/doctors-page';
import { SchedulesPage } from './schedules/schedules-page';
import { SpecialtiesPage } from './specialties/specialties-page';
import { UsersPage } from './users/users-page';

/**
 * Rutas del módulo de Administración, rol ADMIN.
 * Cada ruta muestra la vista de su caso de uso (CU-021 a CU-027).
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
      { path: 'usuarios', element: <UsersPage /> },
      { path: 'medicos', element: <DoctorsPage /> },
      { path: 'especialidades', element: <SpecialtiesPage /> },
      { path: 'horarios', element: <SchedulesPage /> },
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
