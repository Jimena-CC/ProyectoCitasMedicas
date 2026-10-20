import * as StatusBadge from '@/components/ui/status-badge';

/** Estado Activo / Inactivo de usuarios, médicos y especialidades (status-badge-demo). */
export function ActiveBadge({ active }: { active: boolean }) {
  return (
    <StatusBadge.Root status={active ? 'completed' : 'disabled'} variant='light'>
      <StatusBadge.Dot />
      {active ? 'Activo' : 'Inactivo'}
    </StatusBadge.Root>
  );
}
