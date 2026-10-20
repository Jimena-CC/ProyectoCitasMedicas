import { Navigate } from 'react-router';

import { roleHome } from '@/features/auth/roles';
import { useCurrentUser } from '@/features/auth/use-auth';

/** "/" → pantalla inicial del rol (RNF-17). */
export function RoleHomeRedirect() {
  const user = useCurrentUser();
  return <Navigate to={roleHome(user.role)} replace />;
}
