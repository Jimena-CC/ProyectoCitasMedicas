import * as React from 'react';
import { RiAddLine, RiHeartPulseLine, RiMapPinLine, RiPencilLine } from '@remixicon/react';
import type { ColumnDef } from '@tanstack/react-table';

import { Card } from '@/components/card';
import { DataTable, SortableHeader } from '@/components/data-table';
import { PageHeader } from '@/components/page-header';
import * as Button from '@/components/ui/button';
import * as Tag from '@/components/ui/tag';
import { getErrorMessage } from '@/lib/api/client';
import type { SpecialtyDetail } from '@/lib/api/types';

import { ActiveBadge } from '../components/active-badge';
import { SearchInput } from '../components/search-input';
import { useLocationsQuery, useSpecialtiesQuery } from '../queries';
import { SpecialtyFormModal } from './specialty-form-modal';

type ModalState = { kind: 'create' } | { kind: 'edit'; specialty: SpecialtyDetail } | null;

/** Especialidades y sus sedes (CU-023, RF-34): listado, alta y edición. */
export function SpecialtiesPage() {
  const specialtiesQuery = useSpecialtiesQuery();
  const locationsQuery = useLocationsQuery();

  const [search, setSearch] = React.useState('');
  const [modal, setModalState] = React.useState<ModalState>(null);
  // Cada apertura remonta el formulario del modal (key) para que parta limpio.
  const [formKey, setFormKey] = React.useState(0);
  const setModal = React.useCallback((next: ModalState) => {
    setModalState(next);
    if (next !== null) setFormKey((k) => k + 1);
  }, []);

  const filtered = React.useMemo(() => {
    const term = search.trim().toLowerCase();
    if (term === '') return specialtiesQuery.data ?? [];
    return (specialtiesQuery.data ?? []).filter((s) => s.name.toLowerCase().includes(term) || (s.description ?? '').toLowerCase().includes(term));
  }, [specialtiesQuery.data, search]);

  const columns = React.useMemo<ColumnDef<SpecialtyDetail>[]>(
    () => [
      {
        accessorKey: 'name',
        header: ({ column }) => <SortableHeader column={column}>Especialidad</SortableHeader>,
        cell: ({ getValue }) => <span className='text-label-sm text-text-strong-950'>{getValue<string>()}</span>,
      },
      {
        accessorKey: 'description',
        header: 'Descripción',
        size: 320,
        cell: ({ getValue }) => {
          const value = getValue<string | null>();
          return value ? (
            <span className='line-clamp-2 max-w-[320px] text-paragraph-sm text-text-sub-600' title={value}>
              {value}
            </span>
          ) : (
            <span className='text-paragraph-sm text-text-soft-400'>—</span>
          );
        },
      },
      {
        accessorKey: 'appointmentMinutes',
        header: ({ column }) => <SortableHeader column={column}>Minutos por cita</SortableHeader>,
        cell: ({ getValue }) => <span className='text-label-sm text-text-strong-950'>{getValue<number>()} min</span>,
      },
      {
        id: 'locations',
        header: 'Sedes',
        cell: ({ row }) =>
          row.original.locations.length === 0 ? (
            <span className='text-paragraph-sm text-text-soft-400'>Sin sedes</span>
          ) : (
            <div className='flex flex-wrap gap-1.5'>
              {row.original.locations.map((location) => (
                <Tag.Root key={location.id} variant='gray'>
                  <Tag.Icon as={RiMapPinLine} />
                  {location.name}
                </Tag.Root>
              ))}
            </div>
          ),
      },
      {
        accessorKey: 'active',
        header: 'Estado',
        cell: ({ getValue }) => <ActiveBadge active={getValue<boolean>()} />,
      },
      {
        id: 'actions',
        header: '',
        size: 96,
        cell: ({ row }) => (
          <div className='flex justify-end'>
            <Button.Root variant='neutral' mode='ghost' size='xsmall' onClick={() => setModal({ kind: 'edit', specialty: row.original })}>
              <Button.Icon as={RiPencilLine} />
              Editar
            </Button.Root>
          </div>
        ),
      },
    ],
    [setModal],
  );

  return (
    <>
      <PageHeader
        eyebrow='Administración'
        title='Especialidades'
        description='Administra las especialidades, la duración de sus citas y las sedes donde se atienden.'
        actions={
          <Button.Root variant='primary' mode='filled' size='small' onClick={() => setModal({ kind: 'create' })}>
            <Button.Icon as={RiAddLine} />
            Nueva especialidad
          </Button.Root>
        }
      />

      <Card className='flex flex-col gap-4'>
        <div className='flex flex-wrap items-center justify-between gap-3'>
          <SearchInput id='specialties-search' value={search} onChange={setSearch} placeholder='Buscar especialidad' />
        </div>

        <DataTable
          columns={columns}
          data={filtered}
          isLoading={specialtiesQuery.isPending}
          errorMessage={specialtiesQuery.isError ? getErrorMessage(specialtiesQuery.error) : null}
          onRetry={() => void specialtiesQuery.refetch()}
          emptyIcon={RiHeartPulseLine}
          emptyTitle={search.trim() !== '' ? 'Ninguna especialidad coincide con la búsqueda' : 'Aún no hay especialidades'}
          emptyDescription={search.trim() !== '' ? 'Prueba con otro nombre.' : 'Crea la primera con el botón "Nueva especialidad".'}
          getRowId={(specialty) => String(specialty.id)}
          pageSize={15}
        />
      </Card>

      <SpecialtyFormModal
        key={formKey}
        open={modal !== null}
        onOpenChange={(open) => !open && setModal(null)}
        specialty={modal?.kind === 'edit' ? modal.specialty : null}
        locations={locationsQuery.data ?? []}
      />
    </>
  );
}
