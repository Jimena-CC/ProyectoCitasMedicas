import { RiComputerLine } from '@remixicon/react';

import * as Avatar from '@/components/ui/avatar';
import * as Tag from '@/components/ui/tag';
import type { AuditUser } from '@/lib/api';
import { initialsOf } from '@/lib/formatters';

const AVATAR_COLORS = ['blue', 'purple', 'sky', 'yellow', 'red'] as const;

function colorFor(username: string) {
  let hash = 0;
  for (const char of username) hash = (hash * 31 + char.charCodeAt(0)) % 997;
  return AVATAR_COLORS[hash % AVATAR_COLORS.length];
}

/** Usuario que ejecutó la acción (avatar con iniciales, ejemplo avatar-text) o etiqueta "Kiosco" cuando actuó el paciente. */
export function AuditUserCell({ user }: { user: AuditUser | null }) {
  if (!user) {
    return (
      <Tag.Root variant='gray'>
        <Tag.Icon as={RiComputerLine} />
        Kiosco
      </Tag.Root>
    );
  }
  const name = user.fullName && user.fullName !== user.username ? user.fullName : null;
  return (
    <div className='flex items-center gap-3'>
      <Avatar.Root size='32' color={colorFor(user.username)}>
        {initialsOf(name ?? user.username)}
      </Avatar.Root>
      <div className='flex min-w-0 flex-col'>
        <span className='truncate text-label-sm text-text-strong-950'>{user.username}</span>
        {name && <span className='truncate text-paragraph-xs text-text-sub-600'>{name}</span>}
      </div>
    </div>
  );
}
