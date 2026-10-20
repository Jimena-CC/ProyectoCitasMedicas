import * as React from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { RiErrorWarningFill, RiGroupLine, RiLockPasswordLine, RiMore2Line, RiPencilLine, RiUserAddLine, RiUserForbidLine, RiUserFollowLine } from '@remixicon/react';
import type { ColumnDef } from '@tanstack/react-table';

import { Card } from '@/components/card';
import { ConfirmModal } from '@/components/confirm-modal';
import { DataTable, SortableHeader } from '@/components/data-table';
import { notify } from '@/components/notify';
import { PageHeader } from '@/components/page-header';
import * as Alert from '@/components/ui/alert';
import * as Button from '@/components/ui/button';
import * as Dropdown from '@/components/ui/dropdown';
import * as SegmentedControl from '@/components/ui/segmented-control';
import { useCurrentUser } from '@/features/auth/use-auth';
import { adminApi } from '@/lib/api/admin';
import { getErrorMessage } from '@/lib/api/client';
import type { UserRole, UserSummary } from '@/lib/api/types';
import { ROLE_LABELS, formatDateTime } from '@/lib/formatters';

import { ActiveBadge } from '../components/active-badge';
import { RoleBadge } from '../components/role-badge';
import { SearchInput } from '../components/search-input';
import { adminKeys, useDoctorsQuery, useUsersQuery } from '../queries';
import { PasswordModal } from './password-modal';
import { UserFormModal } from './user-form-modal';

type RoleFilter = 'ALL' | UserRole;

const ROLE_FILTERS: { value: RoleFilter; label: string }[] = [
  { value: 'ALL', label: 'Todos' },
  { value: 'ADMIN', label: ROLE_LABELS.ADMIN },
  { value: 'FRONT_DESK', label: ROLE_LABELS.FRONT_DESK },
  { value: 'DOCTOR', label: ROLE_LABELS.DOCTOR },
];

type ModalState = { kind: 'create' } | { kind: 'edit'; user: UserSummary } | { kind: 'password'; user: UserSummary } | { kind: 'toggle'; user: UserSummary } | null;

/** Usuarios del backoffice (CU-021, RF-32): listado, alta, edición, contraseña y activación. */
export function UsersPage() {
  const currentUser = useCurrentUser();
  const queryClient = useQueryClient();
  const usersQuery = useUsersQuery();
  const doctorsQuery = useDoctorsQuery();

  const [search, setSearch] = React.useState('');
  const [roleFilter, setRoleFilter] = React.useState<RoleFilter>('ALL');
  const [modal, setModalState] = React.useState<ModalState>(null);
  // Cada apertura remonta el formulario del modal (key) para que parta limpio.
  const [formKey, setFormKey] = React.useState(0);
  const setModal = React.useCallback((next: ModalState) => {
    setModalState(next);
    if (next !== null) setFormKey((k) => k + 1);
  }, []);
  const [toggleError, setToggleError] = React.useState<string | null>(null);

  const doctorNames = React.useMemo(() => new Map((doctorsQuery.data ?? []).map((d) => [d.id, d.fullName])), [doctorsQuery.data]);

  const filtered = React.useMemo(() => {
    const term = search.trim().toLowerCase();
    return (usersQuery.data ?? []).filter((user) => {
      if (roleFilter !== 'ALL' && user.role !== roleFilter) return false;
      if (term === '') return true;
      const doctorName = user.doctorId ? (doctorNames.get(user.doctorId) ?? '') : '';
      return [user.username, user.fullName, doctorName].some((value) => value.toLowerCase().includes(term));
    });
  }, [usersQuery.data, search, roleFilter, doctorNames]);

  const toggleMutation = useMutation({
    mutationFn: (user: UserSummary) =>
      adminApi.updateUser(user.id, {
        role: user.role,
        doctorId: user.doctorId,
        active: !user.active,
      }),
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: adminKeys.users });
      notify.success(saved.active ? `Se activó el usuario ${saved.username}.` : `Se desactivó el usuario ${saved.username}.`);
      setModal(null);
    },
    onError: (error) => setToggleError(getErrorMessage(error)),
  });

  const columns = React.useMemo<ColumnDef<UserSummary>[]>(
    () => [
      {
        accessorKey: 'username',
        header: ({ column }) => <SortableHeader column={column}>Usuario</SortableHeader>,
        cell: ({ row }) => (
          <div className='flex flex-col gap-0.5'>
            <span className='text-label-sm text-text-strong-950'>{row.original.username}</span>
            {row.original.id === currentUser.id && <span className='text-paragraph-xs text-text-sub-600'>Tu usuario</span>}
          </div>
        ),
      },
      {
        accessorKey: 'fullName',
        header: ({ column }) => <SortableHeader column={column}>Nombre</SortableHeader>,
        cell: ({ getValue }) => <span className='text-paragraph-sm text-text-sub-600'>{getValue<string>()}</span>,
      },
      {
        accessorKey: 'role',
        header: 'Perfil',
        cell: ({ getValue }) => <RoleBadge role={getValue<UserRole>()} />,
      },
      {
        id: 'doctor',
        header: 'Médico vinculado',
        accessorFn: (user) => (user.doctorId ? (doctorNames.get(user.doctorId) ?? `Médico #${user.doctorId}`) : ''),
        cell: ({ getValue }) => {
          const name = getValue<string>();
          return name ? <span className='text-label-sm text-text-strong-950'>{name}</span> : <span className='text-paragraph-sm text-text-soft-400'>—</span>;
        },
      },
      {
        accessorKey: 'active',
        header: 'Estado',
        cell: ({ getValue }) => <ActiveBadge active={getValue<boolean>()} />,
      },
      {
        accessorKey: 'lastAccess',
        header: ({ column }) => <SortableHeader column={column}>Último acceso</SortableHeader>,
        cell: ({ getValue }) => {
          const value = getValue<string | null>();
          return value ? (
            <span className='text-paragraph-sm text-text-sub-600'>{formatDateTime(value)}</span>
          ) : (
            <span className='text-paragraph-sm text-text-soft-400'>Nunca</span>
          );
        },
      },
      {
        id: 'actions',
        header: '',
        size: 56,
        cell: ({ row }) => {
          const user = row.original;
          return (
            <div className='flex justify-end'>
              <Dropdown.Root>
                <Dropdown.Trigger asChild>
                  <Button.Root variant='neutral' mode='ghost' size='xsmall' aria-label={`Acciones de ${user.username}`}>
                    <Button.Icon as={RiMore2Line} />
                  </Button.Root>
                </Dropdown.Trigger>
                <Dropdown.Content align='end'>
                  <Dropdown.Item onSelect={() => setModal({ kind: 'edit', user })}>
                    <Dropdown.ItemIcon as={RiPencilLine} />
                    Editar
                  </Dropdown.Item>
                  <Dropdown.Item onSelect={() => setModal({ kind: 'password', user })}>
                    <Dropdown.ItemIcon as={RiLockPasswordLine} />
                    Cambiar contraseña
                  </Dropdown.Item>
                  <Dropdown.Item
                    onSelect={() => {
                      setToggleError(null);
                      setModal({ kind: 'toggle', user });
                    }}
                  >
                    <Dropdown.ItemIcon as={user.active ? RiUserForbidLine : RiUserFollowLine} />
                    {user.active ? 'Desactivar' : 'Activar'}
                  </Dropdown.Item>
                </Dropdown.Content>
              </Dropdown.Root>
            </div>
          );
        },
      },
    ],
    [currentUser.id, doctorNames, setModal],
  );

  const toggleTarget = modal?.kind === 'toggle' ? modal.user : null;
  const isFiltering = search.trim() !== '' || roleFilter !== 'ALL';

  return (
    <>
      <PageHeader
        eyebrow='Administración'
        title='Usuarios'
        description='Accesos del personal al backoffice: administradores, admisión y médicos.'
        actions={
          <Button.Root variant='primary' mode='filled' size='small' onClick={() => setModal({ kind: 'create' })}>
            <Button.Icon as={RiUserAddLine} />
            Nuevo usuario
          </Button.Root>
        }
      />

      <Card className='flex flex-col gap-4'>
        <div className='flex flex-wrap items-center justify-between gap-3'>
          <SearchInput id='users-search' value={search} onChange={setSearch} placeholder='Buscar por usuario, nombre o médico' />
          <SegmentedControl.Root value={roleFilter} onValueChange={(value) => setRoleFilter(value as RoleFilter)} className='w-auto'>
            <SegmentedControl.List>
              {ROLE_FILTERS.map((filter) => (
                <SegmentedControl.Trigger key={filter.value} value={filter.value}>
                  {filter.label}
                </SegmentedControl.Trigger>
              ))}
            </SegmentedControl.List>
          </SegmentedControl.Root>
        </div>

        <DataTable
          columns={columns}
          data={filtered}
          isLoading={usersQuery.isPending}
          errorMessage={usersQuery.isError ? getErrorMessage(usersQuery.error) : null}
          onRetry={() => void usersQuery.refetch()}
          emptyIcon={RiGroupLine}
          emptyTitle={isFiltering ? 'Ningún usuario coincide con la búsqueda' : 'Aún no hay usuarios'}
          emptyDescription={isFiltering ? 'Prueba con otro nombre o cambia el filtro de perfil.' : 'Crea el primer usuario con el botón "Nuevo usuario".'}
          getRowId={(user) => String(user.id)}
          pageSize={15}
        />
      </Card>

      <UserFormModal
        key={`user-${formKey}`}
        open={modal?.kind === 'create' || modal?.kind === 'edit'}
        onOpenChange={(open) => !open && setModal(null)}
        user={modal?.kind === 'edit' ? modal.user : null}
        users={usersQuery.data ?? []}
        doctors={doctorsQuery.data ?? []}
      />

      <PasswordModal
        key={`password-${formKey}`}
        open={modal?.kind === 'password'}
        onOpenChange={(open) => !open && setModal(null)}
        user={modal?.kind === 'password' ? modal.user : null}
      />

      <ConfirmModal
        open={toggleTarget !== null}
        onOpenChange={(open) => !open && setModal(null)}
        status={toggleTarget?.active ? 'error' : 'information'}
        icon={toggleTarget?.active ? RiUserForbidLine : RiUserFollowLine}
        title={toggleTarget?.active ? `¿Desactivar a ${toggleTarget.username}?` : `¿Activar a ${toggleTarget?.username}?`}
        description={
          toggleTarget?.active
            ? 'La persona no podrá iniciar sesión hasta que vuelvas a activarla. Si está trabajando ahora, su sesión se cerrará.'
            : 'La persona volverá a poder iniciar sesión con su contraseña actual.'
        }
        confirmLabel={toggleTarget?.active ? 'Desactivar' : 'Activar'}
        isPending={toggleMutation.isPending}
        onConfirm={() => {
          if (toggleTarget) toggleMutation.mutate(toggleTarget);
        }}
      >
        {toggleError && (
          <Alert.Root variant='lighter' status='error' size='small'>
            <Alert.Icon as={RiErrorWarningFill} />
            {toggleError}
          </Alert.Root>
        )}
      </ConfirmModal>
    </>
  );
}
