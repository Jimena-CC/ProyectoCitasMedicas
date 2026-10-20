import * as Badge from '@/components/ui/badge';
import { auditActionKind, auditActionLabel, type AuditActionKind } from '@/features/admin/audit/audit-labels';

const KIND_COLOR: Record<AuditActionKind, 'green' | 'blue' | 'red' | 'gray'> = {
  created: 'green',
  changed: 'blue',
  removed: 'red',
  login: 'gray',
};

/** Distintivo de la acción auditada (ejemplo badge-colors): creación verde, cambio azul, anulación roja, sesión gris. */
export function AuditActionBadge({ action }: { action: string }) {
  return (
    <Badge.Root variant='light' color={KIND_COLOR[auditActionKind(action)]} size='medium'>
      {auditActionLabel(action)}
    </Badge.Root>
  );
}
