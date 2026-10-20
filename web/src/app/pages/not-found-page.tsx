import { Link } from 'react-router';
import { RiArrowLeftLine, RiCompassDiscoverLine } from '@remixicon/react';

import { Card } from '@/components/card';
import { EmptyState } from '@/components/empty-state';
import { PageHeader } from '@/components/page-header';
import * as Button from '@/components/ui/button';
import { roleHome } from '@/features/auth/roles';
import { useCurrentUser } from '@/features/auth/use-auth';

/** 404 amigable dentro del backoffice. */
export function NotFoundPage() {
  const user = useCurrentUser();
  return (
    <>
      <PageHeader title='Página no encontrada' />
      <Card>
        <EmptyState
          icon={RiCompassDiscoverLine}
          title='No encontramos esta página'
          description='La dirección que ingresaste no existe o fue movida. Usa el menú de la izquierda para continuar.'
          action={
            <Button.Root variant='neutral' mode='stroke' size='small' asChild>
              <Link to={roleHome(user.role)}>
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
