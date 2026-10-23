import { RiErrorWarningLine } from '@remixicon/react';

import { Card } from '@/components/card';
import { EmptyState } from '@/components/empty-state';
import * as Button from '@/components/ui/button';
import { getErrorMessage } from '@/lib/api';

/** Tarjeta de error con reintento para un reporte que no se pudo cargar. */
export function ReportError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  return (
    <Card>
      <EmptyState
        icon={RiErrorWarningLine}
        title='No pudimos cargar el reporte'
        description={getErrorMessage(error)}
        action={
          <Button.Root variant='neutral' mode='stroke' size='small' onClick={onRetry}>
            Reintentar
          </Button.Root>
        }
      />
    </Card>
  );
}
