import { createBrowserRouter } from 'react-router';

import { AppShell } from '@/app/app-shell';
import { NotFoundPage } from '@/app/pages/not-found-page';
import { RoleHomeRedirect } from '@/app/pages/role-home-redirect';
import { RouteErrorPage } from '@/app/pages/route-error-page';
import { adminRoutes } from '@/features/admin/routes';
import { admissionRoutes } from '@/features/admission/routes';
import { LoginPage } from '@/features/auth/login-page';
import { RequireAuth } from '@/features/auth/require-auth';
import { doctorRoutes } from '@/features/doctor/routes';

/**
 * Árbol de rutas del backoffice.
 *
 *   /login                       pública
 *   /                            redirige al inicio del rol
 *   /ventanilla/**               FRONT_DESK y ADMIN   (src/features/admission/routes.tsx)
 *   /agenda                      DOCTOR               (src/features/doctor/routes.tsx)
 *   /administracion/**           ADMIN                (src/features/admin/routes.tsx)
 */
export const router = createBrowserRouter([
  {
    errorElement: <RouteErrorPage />,
    children: [
      { path: '/login', element: <LoginPage /> },
      {
        element: <RequireAuth />,
        children: [
          {
            element: <AppShell />,
            children: [
              { index: true, element: <RoleHomeRedirect /> },
              ...admissionRoutes,
              ...doctorRoutes,
              ...adminRoutes,
              { path: '*', element: <NotFoundPage /> },
            ],
          },
        ],
      },
    ],
  },
]);
