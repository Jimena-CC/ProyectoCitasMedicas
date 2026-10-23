import { NavLink } from 'react-router';
import { RiExpandUpDownLine, RiLogoutBoxRLine } from '@remixicon/react';

import { BrandLogo } from '@/components/brand-logo';
import * as Avatar from '@/components/ui/avatar';
import * as Divider from '@/components/ui/divider';
import * as Dropdown from '@/components/ui/dropdown';
import { navGroupsFor } from '@/app/navigation';
import { useAuth, useCurrentUser } from '@/features/auth/use-auth';
import type { UserRole } from '@/lib/api/types';
import { cn } from '@/lib/cn';
import { formatRole, initialsOf } from '@/lib/formatters';

const AVATAR_COLOR: Record<UserRole, 'blue' | 'purple' | 'sky'> = {
  ADMIN: 'purple',
  FRONT_DESK: 'blue',
  DOCTOR: 'sky',
};

/** Barra lateral: logo, grupos de navegación según el rol y tarjeta de usuario con menú (dropdown-demo). */
export function Sidebar() {
  const user = useCurrentUser();
  const { logout } = useAuth();
  const groups = navGroupsFor(user.role);

  return (
    <aside className='flex w-64 shrink-0 flex-col gap-4 border-r border-stroke-soft-200 bg-bg-white-0 px-4 py-5 [view-transition-name:app-sidebar]'>
      <div className='flex flex-col gap-2 px-2 pt-1'>
        <BrandLogo className='h-auto w-full text-primary-dark' />
        <div className='text-paragraph-xs text-text-sub-600'>MediCitas · Backoffice</div>
      </div>

      <Divider.Root />

      <nav className='flex flex-1 flex-col gap-5 overflow-y-auto' aria-label='Navegación principal'>
        {groups.map((group) => (
          <div key={group.label} className='flex flex-col gap-1'>
            <div className='px-2 pb-1 text-subheading-xs uppercase text-text-soft-400'>{group.label}</div>
            {group.items.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                viewTransition
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-2 rounded-10 px-3 py-2 text-label-sm transition-colors',
                    isActive ? 'bg-bg-weak-50 text-text-strong-950' : 'text-text-sub-600 hover:bg-bg-weak-50 hover:text-text-strong-950',
                  )
                }
              >
                {({ isActive }) => (
                  <>
                    <item.icon className={cn('size-5 shrink-0', isActive ? 'text-primary-base' : 'text-text-sub-600')} />
                    <span className='truncate'>{item.label}</span>
                  </>
                )}
              </NavLink>
            ))}
          </div>
        ))}
      </nav>

      <Dropdown.Root>
        <Dropdown.Trigger asChild>
          <button
            type='button'
            className='flex w-full items-center gap-3 rounded-10 p-2 text-left ring-1 ring-inset ring-stroke-soft-200 transition-colors hover:bg-bg-weak-50 focus-visible:outline-none focus-visible:ring-stroke-strong-950'
            aria-label='Menú de usuario'
          >
            <Avatar.Root size='40' color={AVATAR_COLOR[user.role]}>
              {initialsOf(user.fullName)}
            </Avatar.Root>
            <div className='min-w-0 flex-1'>
              <div className='truncate text-label-sm text-text-strong-950'>{user.fullName}</div>
              <div className='truncate text-paragraph-xs text-text-sub-600'>{formatRole(user.role)}</div>
            </div>
            <RiExpandUpDownLine className='size-5 shrink-0 text-text-soft-400' />
          </button>
        </Dropdown.Trigger>
        <Dropdown.Content align='start' side='top' className='w-[224px]'>
          <div className='flex items-center gap-3 p-2'>
            <Avatar.Root size='40' color={AVATAR_COLOR[user.role]}>
              {initialsOf(user.fullName)}
            </Avatar.Root>
            <div className='min-w-0 flex-1'>
              <div className='truncate text-label-sm text-text-strong-950'>{user.fullName}</div>
              <div className='mt-1 truncate text-paragraph-xs text-text-sub-600'>
                {user.username} · {formatRole(user.role)}
              </div>
            </div>
          </div>
          <Divider.Root variant='line-spacing' />
          <Dropdown.Group>
            <Dropdown.Item onSelect={() => logout('manual')}>
              <Dropdown.ItemIcon as={RiLogoutBoxRLine} />
              Cerrar sesión
            </Dropdown.Item>
          </Dropdown.Group>
        </Dropdown.Content>
      </Dropdown.Root>
    </aside>
  );
}
