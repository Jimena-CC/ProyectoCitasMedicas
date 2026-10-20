import { RiToolsLine, type RemixiconComponentType } from '@remixicon/react';

import { Card } from '@/components/card';
import { EmptyState } from '@/components/empty-state';
import { PageHeader } from '@/components/page-header';

interface UnderConstructionPageProps {
  title: string;
  description?: string;
  icon?: RemixiconComponentType;
}

/** Página provisional para rutas planificadas que la siguiente fase reemplaza por la vista real. */
export function UnderConstructionPage({ title, description, icon = RiToolsLine }: UnderConstructionPageProps) {
  return (
    <>
      <PageHeader title={title} description={description} />
      <Card>
        <EmptyState
          icon={icon}
          title='En construcción'
          description='Esta sección estará disponible en la siguiente fase del proyecto.'
        />
      </Card>
    </>
  );
}
