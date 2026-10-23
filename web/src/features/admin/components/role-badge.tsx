import * as Badge from '@/components/ui/badge';
import type { UserRole } from '@/lib/api/types';
import { ROLE_LABELS } from '@/lib/formatters';

const ROLE_COLORS: Record<UserRole, 'purple' | 'blue' | 'teal'> = {
  ADMIN: 'purple',
  FRONT_DESK: 'blue',
  DOCTOR: 'teal',
};

/** Perfil del usuario con un color por rol (ejemplo badge-colors). */
export function RoleBadge({ role }: { role: UserRole }) {
  return (
    <Badge.Root variant='light' color={ROLE_COLORS[role]} size='medium'>
      {ROLE_LABELS[role]}
    </Badge.Root>
  );
}
