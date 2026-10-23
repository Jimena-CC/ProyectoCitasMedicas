import { Link } from 'react-router';
import { RiArrowLeftLine, RiLockLine } from '@remixicon/react';

import { Card } from '@/components/card';
import { EmptyState } from '@/components/empty-state';
import { PageHeader } from '@/components/page-header';
import * as Button from '@/components/ui/button';
import { roleHome } from '@/features/auth/roles';
import { useCurrentUser } from '@/features/auth/use-auth';
import { formatRole } from '@/lib/formatters';

/** 403 amigable: el rol del usuario no puede entrar a esta sección (RNF-17). */
export function ForbiddenPage() {
  const user = useCurrentUser();
  return (
    <>
      <PageHeader title='Acceso restringido' />
      <Card>
        <EmptyState
          icon={RiLockLine}
          title='No tienes acceso a esta sección'
          description={`Tu perfil de ${formatRole(user.role)} no incluye esta opción. Si crees que es un error, comunícate con el administrador del sistema.`}
          action={
            <Button.Root variant='neutral' mode='stroke' size='small' asChild>
              <Link viewTransition to={roleHome(user.role)}>
                <Button.Icon as={RiArrowLeftLine} />
                Ir a mi inicio
              </Link>
            </Button.Root>
          }
        />
      </Card>
    </>
  );
}
