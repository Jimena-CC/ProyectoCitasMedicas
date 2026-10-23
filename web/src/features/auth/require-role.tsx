import { Outlet } from 'react-router';

import { ForbiddenPage } from '@/app/pages/forbidden-page';
import { useCurrentUser } from '@/features/auth/use-auth';
import type { UserRole } from '@/lib/api/types';

interface RequireRoleProps {
  roles: UserRole[];
}

/** Muestra un 403 amigable si el rol del usuario no está en `roles` (RNF-17). Usar dentro de RequireAuth. */
export function RequireRole({ roles }: RequireRoleProps) {
  const user = useCurrentUser();
  if (!roles.includes(user.role)) return <ForbiddenPage />;
  return <Outlet />;
}
