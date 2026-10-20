import * as React from 'react';
import { RiPencilLine, RiStethoscopeLine, RiUserAddLine } from '@remixicon/react';
import type { ColumnDef } from '@tanstack/react-table';

import { Card } from '@/components/card';
import { DataTable, SortableHeader } from '@/components/data-table';
import { PageHeader } from '@/components/page-header';
import * as Button from '@/components/ui/button';
import * as Select from '@/components/ui/select';
import { getErrorMessage } from '@/lib/api/client';
import type { DoctorDetail } from '@/lib/api/types';

import { ActiveBadge } from '../components/active-badge';
import { SearchInput } from '../components/search-input';
import { useDoctorsQuery, useSpecialtiesQuery } from '../queries';
import { DoctorFormModal } from './doctor-form-modal';

const ALL = 'ALL';

type ModalState = { kind: 'create' } | { kind: 'edit'; doctor: DoctorDetail } | null;

/** Médicos de la clínica (CU-022, RF-33): listado, alta y edición. */
export function DoctorsPage() {
  const doctorsQuery = useDoctorsQuery();
  const specialtiesQuery = useSpecialtiesQuery();

  const [search, setSearch] = React.useState('');
  const [specialtyFilter, setSpecialtyFilter] = React.useState(ALL);
  const [modal, setModalState] = React.useState<ModalState>(null);
  // Cada apertura remonta el formulario del modal (key) para que parta limpio.
  const [formKey, setFormKey] = React.useState(0);
  const setModal = React.useCallback((next: ModalState) => {
    setModalState(next);
    if (next !== null) setFormKey((k) => k + 1);
  }, []);

  const filtered = React.useMemo(() => {
    const term = search.trim().toLowerCase();
    return (doctorsQuery.data ?? []).filter((doctor) => {
      if (specialtyFilter !== ALL && String(doctor.specialty.id) !== specialtyFilter) return false;
      if (term === '') return true;
      return [doctor.fullName, doctor.license, doctor.specialistRegistry ?? ''].some((value) => value.toLowerCase().includes(term));
    });
  }, [doctorsQuery.data, search, specialtyFilter]);

  const columns = React.useMemo<ColumnDef<DoctorDetail>[]>(
    () => [
      {
        accessorKey: 'fullName',
        header: ({ column }) => <SortableHeader column={column}>Médico</SortableHeader>,
        cell: ({ row }) => (
          <div className='flex flex-col gap-0.5'>
            <span className='text-label-sm text-text-strong-950'>{row.original.fullName}</span>
            <span className='text-paragraph-xs text-text-sub-600'>
              {row.original.lastNames}, {row.original.firstNames}
            </span>
          </div>
        ),
      },
      {
        accessorKey: 'license',
        header: 'Colegiatura',
        cell: ({ getValue }) => <span className='text-label-sm text-text-strong-950'>CMP {getValue<string>()}</span>,
      },
      {
        accessorKey: 'specialistRegistry',
        header: 'Registro de especialista',
        cell: ({ getValue }) => {
          const value = getValue<string | null>();
          return value ? <span className='text-paragraph-sm text-text-sub-600'>RNE {value}</span> : <span className='text-paragraph-sm text-text-soft-400'>—</span>;
        },
      },
      {
        id: 'specialty',
        accessorFn: (doctor) => doctor.specialty.name,
        header: ({ column }) => <SortableHeader column={column}>Especialidad</SortableHeader>,
        cell: ({ getValue }) => <span className='text-paragraph-sm text-text-sub-600'>{getValue<string>()}</span>,
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
            <Button.Root variant='neutral' mode='ghost' size='xsmall' onClick={() => setModal({ kind: 'edit', doctor: row.original })}>
              <Button.Icon as={RiPencilLine} />
              Editar
            </Button.Root>
          </div>
        ),
      },
    ],
    [setModal],
  );

  const isFiltering = search.trim() !== '' || specialtyFilter !== ALL;

  return (
    <>
      <PageHeader
        eyebrow='Administración'
        title='Médicos'
        description='Registra y actualiza los médicos de la clínica y su especialidad.'
        actions={
          <Button.Root variant='primary' mode='filled' size='small' onClick={() => setModal({ kind: 'create' })}>
            <Button.Icon as={RiUserAddLine} />
            Nuevo médico
          </Button.Root>
        }
      />

      <Card className='flex flex-col gap-4'>
        <div className='flex flex-wrap items-center justify-between gap-3'>
          <SearchInput id='doctors-search' value={search} onChange={setSearch} placeholder='Buscar por nombre o colegiatura' />
          <div className='w-[260px]'>
            <Select.Root size='small' value={specialtyFilter} onValueChange={setSpecialtyFilter}>
              <Select.Trigger aria-label='Filtrar por especialidad'>
                <Select.Value placeholder='Todas las especialidades' />
              </Select.Trigger>
              <Select.Content>
                <Select.Item value={ALL}>Todas las especialidades</Select.Item>
                {(specialtiesQuery.data ?? []).map((specialty) => (
                  <Select.Item key={specialty.id} value={String(specialty.id)}>
                    {specialty.name}
                  </Select.Item>
                ))}
              </Select.Content>
            </Select.Root>
          </div>
        </div>

        <DataTable
          columns={columns}
          data={filtered}
          isLoading={doctorsQuery.isPending}
          errorMessage={doctorsQuery.isError ? getErrorMessage(doctorsQuery.error) : null}
          onRetry={() => void doctorsQuery.refetch()}
          emptyIcon={RiStethoscopeLine}
          emptyTitle={isFiltering ? 'Ningún médico coincide con la búsqueda' : 'Aún no hay médicos registrados'}
          emptyDescription={isFiltering ? 'Prueba con otro nombre o cambia la especialidad.' : 'Registra el primero con el botón "Nuevo médico".'}
          getRowId={(doctor) => String(doctor.id)}
          pageSize={15}
        />
      </Card>

      <DoctorFormModal
        key={formKey}
        open={modal !== null}
        onOpenChange={(open) => !open && setModal(null)}
        doctor={modal?.kind === 'edit' ? modal.doctor : null}
        specialties={specialtiesQuery.data ?? []}
      />
    </>
  );
}
